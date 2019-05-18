package com.netflix.client.config;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Base implementation of an IClientConfig with configuration that can be reloaded at runtime from an underlying
 * property source while optimizing access to property values.
 *
 * Properties can either be scoped to a specific client or default properties that span all clients.   By default
 * properties follow the name convention `{clientname}.{namespace}.{key}` and then fallback to `{namespace}.{key}`
 * if not found
 *
 * Internally the config tracks two maps, one for dynamic properties and one for code settable default values to use
 * when a property is not defined in the underlying property source.
 */
public abstract class ReloadableClientConfig implements IClientConfig {
    private static final Logger LOG = LoggerFactory.getLogger(ReloadableClientConfig.class);

    private static final String DEFAULT_CLIENT_NAME = "";
    private static final String DEFAULT_NAMESPACE = "ribbon";

    // Map of raw property names (without namespace or client) to values set via code
    private final Map<String, Object> defaultProperties = new HashMap<>();

    // Map of all seen dynamic properties.  This map will hold on properties requested with the exception of
    // those returned from getGlobalProperty().
    private final Map<IClientConfigKey, ReloadableProperty<?>> dynamicProperties = new ConcurrentHashMap<>();

    // List of actions to perform when configuration changes.  This includes both updating the Property instances
    // as well as external consumers.
    private final List<Runnable> changeActions = new CopyOnWriteArrayList<>();

    private final AtomicLong refreshCounter = new AtomicLong();

    private final PropertyResolver resolver;

    private String clientName;

    private String namespace = DEFAULT_NAMESPACE;

    private boolean isDynamic;

    protected ReloadableClientConfig(PropertyResolver resolver) {
        this(resolver, DEFAULT_CLIENT_NAME);
        this.isDynamic = false;
    }

    protected ReloadableClientConfig(PropertyResolver resolver, String clientName) {
        this.clientName = clientName;
        this.isDynamic = true;
        this.resolver = resolver;

        resolver.onChange(this::reload);
    }

    protected PropertyResolver getPropertyResolver() {
        return this.resolver;
    }

    /**
     * Refresh all seen properties from the underlying property storage
     */
    public final void reload() {
        changeActions.forEach(Runnable::run);
        cachedToString = null;
    }

    public void setClientName(String clientName){
        this.clientName  = clientName;
    }

    @Override
    public final String getClientName() {
        return clientName;
    }

    @Override
    public String getNameSpace() {
        return namespace;
    }

    @Override
    public final void setNameSpace(String nameSpace) {
        this.namespace = nameSpace;
    }

    @Override
    public void loadProperties(String clientName) {
        this.isDynamic = true;
        this.clientName = clientName;
        loadDefaultValues();
    }

    @Override
    public void loadDefaultValues() {
        this.isDynamic = true;
        reload();
    }

    @Override
    public final Map<String, Object> getProperties() {
        Map<String, Object> result = new HashMap<>(dynamicProperties.size());

        dynamicProperties.forEach((key, prop) ->
            prop.get().ifPresent(value -> result.put(key.key(), String.valueOf(value)))
        );

        LOG.info(result.toString());
        return result;
    }

    @Override
    public void forEachDefault(BiConsumer<IClientConfigKey<?>, Object> consumer) {
        dynamicProperties.forEach((key, value) -> consumer.accept(key, defaultProperties.get(key.key())));
    }

    @Override
    public void forEach(BiConsumer<IClientConfigKey<?>, Object> consumer) {
        dynamicProperties.forEach((key, value) -> consumer.accept(key, value.get().orElse(null)));
    }

    private <T> ReloadableProperty<T> createProperty(final Supplier<Optional<T>> valueSupplier, final Supplier<T> defaultSupplier, final boolean isDynamic) {
        Preconditions.checkNotNull(valueSupplier, "defaultValueSupplier cannot be null");

        return new ReloadableProperty<T>() {
            private volatile Optional<T> value = Optional.empty();

            {
                refresh();
                if (isDynamic) {
                    changeActions.add(this::refresh);
                }
            }

            @Override
            public void onChange(Consumer<T> consumer) {
                final AtomicReference<Optional<T>> previous = new AtomicReference<>(get());
                changeActions.add(() -> {
                    Optional<T> current = get();
                    if (!current.equals(Optional.ofNullable(previous.get()))) {
                        previous.set(current);
                        consumer.accept(current.orElseGet(defaultSupplier::get));
                    }
                });
            }

            @Override
            public Optional<T> get() {
                return value;
            }

            @Override
            public T getOrDefault() {
                return value.orElse(defaultSupplier.get());
            }

            @Override
            public void refresh() {
                refreshCounter.incrementAndGet();
                value = valueSupplier.get();
            }

            @Override
            public String toString() {
                return String.valueOf(get());
            }
        };
    }

    @Override
    public final <T> T get(IClientConfigKey<T> key) {
        return getInternal(key).get().orElse(null);
    }

    public final <T> ReloadableProperty<T> getInternal(IClientConfigKey<T> key) {
        return (ReloadableProperty<T>)dynamicProperties.computeIfAbsent(key, ignore -> getClientDynamicProperty(key, isDynamic));
    }

    @Override
    public final <T> Property<T> getGlobalProperty(IClientConfigKey<T> key) {
        LOG.debug("Get global property {} default {}", key.key(), key.defaultValue());

        return (Property<T>) dynamicProperties.computeIfAbsent(key, ignore -> createProperty(
                () -> resolver.get(key.key(), key.type()),
                key::defaultValue,
                true));
    }

    interface ReloadableProperty<T> extends Property<T> {
        void refresh();
    }

    private <T> ReloadableProperty<T> getClientDynamicProperty(IClientConfigKey<T> key, boolean isDynamic) {
        LOG.debug("Get dynamic property key={} ns={} client={}", key.key(), getNameSpace(), clientName);

        return createProperty(
                () -> resolveFinalProperty(key),
                key::defaultValue,
                isDynamic);
    }

    /**
     * Resolve a properties final value in the following order or precedence
     * - client scope
     * - default scope
     * - internally set default
     * - IClientConfigKey defaultValue
     * @param key
     * @param <T>
     * @return
     */
    private <T> Optional<T> resolveFinalProperty(IClientConfigKey<T> key) {
        Optional<T> value;
        if (!StringUtils.isEmpty(clientName)) {
            value = resolver.get(clientName + "." + getNameSpace() + "." + key.key(), key.type());
            if (value.isPresent()) {
                return value;
            }
        }

        value = resolver.get(getNameSpace() + "." + key.key(), key.type());
        if (value.isPresent()) {
            return value;
        }

        return getIfSet(key);
    }

    private <T> Optional<T> resolverScopedProperty(IClientConfigKey<T> key) {
        Optional<T> value = resolver.get(clientName + "." + getNameSpace() + "." + key.key(), key.type());
        if (value.isPresent()) {
            return value;
        }

        return getIfSet(key);
    }

    @Override
    public <T> Optional<T> getIfSet(IClientConfigKey<T> key) {
        return Optional.ofNullable(defaultProperties.get(key.key()))
                .map(value -> {
                    final Class<T> type = key.type();
                    // Unfortunately there's some legacy code setting string values for typed keys.  Here are do our best to parse
                    // and store the typed value
                    if (!value.getClass().equals(type)) {
                        try {
                            if (type.equals(String.class)) {
                                return (T) value.toString();
                            } else if (value.getClass().equals(String.class)) {
                                final String strValue = (String) value;
                                if (Integer.class.equals(type)) {
                                    return (T) Integer.valueOf(strValue);
                                } else if (Boolean.class.equals(type)) {
                                    return (T) Boolean.valueOf(strValue);
                                } else if (Float.class.equals(type)) {
                                    return (T) Float.valueOf(strValue);
                                } else if (Long.class.equals(type)) {
                                    return (T) Long.valueOf(strValue);
                                } else if (Double.class.equals(type)) {
                                    return (T) Double.valueOf(strValue);
                                } else if (TimeUnit.class.equals(type)) {
                                    return (T) TimeUnit.valueOf(strValue);
                                } else {
                                    return PropertyResolver.resolveWithValueOf(type, strValue)
                                        .orElseThrow(() -> new IllegalArgumentException("Unsupported value type `" + type + "'"));
                                }
                            } else {
                                return PropertyResolver.resolveWithValueOf(type, value.toString())
                                        .orElseThrow(() -> new IllegalArgumentException("Incompatible value type `" + value.getClass() + "` while expecting '" + type + "`"));
                            }
                        } catch (Exception e) {
                            throw new IllegalArgumentException("Error parsing value '" + value + "' for '" + key.key() + "'", e);
                        }
                    } else {
                        return (T)value;
                    }
                });
    }

    @Override
    public final <T> Property<T> getDynamicProperty(IClientConfigKey<T> key) {
        return getClientDynamicProperty(key, true);
    }

    @Override
    public <T> Property<T> getScopedProperty(IClientConfigKey<T> key) {
        return (Property<T>) dynamicProperties.computeIfAbsent(key, ignore -> createProperty(
                () -> resolverScopedProperty(key),
                key::defaultValue,
                isDynamic));
    }

    @Override
    public <T> Property<T> getPrefixMappedProperty(IClientConfigKey<T> key) {
        return (Property<T>) dynamicProperties.computeIfAbsent(key, ignore -> createProperty(
                getPrefixedMapPropertySupplier(key),
                key::defaultValue,
                isDynamic));
    }

    private <T> Supplier<Optional<T>> getPrefixedMapPropertySupplier(IClientConfigKey<T> key) {
        final Method method;
        try {
            method = key.type().getDeclaredMethod("valueOf", Map.class);
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("Class '" + key.type().getName() + "' must have static method valueOf(Map<String, String>)", e);
        }

        return () -> {
            final Map<String, String> values = new HashMap<>();

            resolver.forEach(getNameSpace() + "." + key.key(), values::put);

            if (!StringUtils.isEmpty(clientName)) {
                resolver.forEach(clientName + "." + getNameSpace() + "." + key.key(), values::put);
            }

            try {
                return Optional.ofNullable((T)method.invoke(null, values));
            } catch (Exception e) {
                LOG.warn("Unable to map value for '{}'", key.key(), e);
                return Optional.empty();
            }
        };
    }

    @Override
    public final <T> T get(IClientConfigKey<T> key, T defaultValue) {
        return Optional.ofNullable(get(key)).orElse(defaultValue);
    }

    @Override
    public final <T> IClientConfig set(IClientConfigKey<T> key, T value) {
        Preconditions.checkArgument(key != null, "key cannot be null");
        // Treat nulls as deletes
        if (value == null) {
            defaultProperties.remove(key.key());
        } else {
            defaultProperties.put(key.key(), value);
        }

        getInternal(key).refresh();
        cachedToString = null;

        return this;
    }

    @Override
    @Deprecated
    public void setProperty(IClientConfigKey key, Object value) {
        Preconditions.checkArgument(value != null, "Value may not be null");
        set(key, value);
    }

    @Override
    @Deprecated
    public Object getProperty(IClientConfigKey key) {
        return getInternal(key).get().orElse(null);
    }

    @Override
    @Deprecated
    public Object getProperty(IClientConfigKey key, Object defaultVal) {
        return getInternal(key).get().orElse(defaultVal);
    }

    @Override
    @Deprecated
    public boolean containsProperty(IClientConfigKey key) {
        return dynamicProperties.containsKey(key.key());
    }

    @Override
    @Deprecated
    public int getPropertyAsInteger(IClientConfigKey key, int defaultValue) {
        return Optional.ofNullable(getProperty(key)).map(Integer.class::cast).orElse(defaultValue);
    }

    @Override
    @Deprecated
    public String getPropertyAsString(IClientConfigKey key, String defaultValue) {
        return Optional.ofNullable(getProperty(key)).map(Object::toString).orElse(defaultValue);
    }

    @Override
    @Deprecated
    public boolean getPropertyAsBoolean(IClientConfigKey key, boolean defaultValue) {
        return Optional.ofNullable(getProperty(key)).map(Boolean.class::cast).orElse(defaultValue);
    }

    public IClientConfig applyOverride(IClientConfig override) {
        if (override == null) {
            return this;
        }

        this.defaultProperties.putAll(override.getProperties());
        reload();
        return this;
    }

    private volatile String cachedToString = null;

    @Override
    public String toString() {
        if (cachedToString == null) {
            String newToString = generateToString();
            cachedToString = newToString;
            return newToString;
        }
        return cachedToString;
    }

    /**
     * @return Number of individual properties refreshed.  This can be used to identify patterns of excessive updates.
     */
    public long getRefreshCount() {
        return refreshCounter.get();
    }

    private String generateToString() {
        return "ClientConfig:" + dynamicProperties.entrySet().stream()
                    .map(t -> {
                        if (t.getKey().key().endsWith("Password")) {
                            return t.getKey() + ":***";
                        }
                        Optional value = t.getValue().get();
                        Object defaultValue = t.getKey().defaultValue();
                        return t.getKey() + ":" + value.orElse(defaultValue);
                    })
                .collect(Collectors.joining(", "));
    }
}
