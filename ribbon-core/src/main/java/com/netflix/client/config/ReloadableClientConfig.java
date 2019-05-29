package com.netflix.client.config;

import com.google.common.base.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // Map of raw property names (without namespace or client name) to values. All values are non-null and properly
    // typed to match the key type
    private final Map<IClientConfigKey, Optional<?>> internalProperties = new ConcurrentHashMap<>();

    private final Map<IClientConfigKey, ReloadableProperty<?>> dynamicProperties = new ConcurrentHashMap<>();

    // List of actions to perform when configuration changes.  This includes both updating the Property instances
    // as well as external consumers.
    private final Map<IClientConfigKey, Runnable> changeActions = new ConcurrentHashMap<>();

    private final AtomicLong refreshCounter = new AtomicLong();

    private final PropertyResolver resolver;

    private String clientName = DEFAULT_CLIENT_NAME;

    private String namespace = DEFAULT_NAMESPACE;

    private boolean isDynamic = false;

    protected ReloadableClientConfig(PropertyResolver resolver) {
        this.resolver = resolver;
    }

    protected PropertyResolver getPropertyResolver() {
        return this.resolver;
    }

    /**
     * Refresh all seen properties from the underlying property storage
     */
    public final void reload() {
        changeActions.values().forEach(Runnable::run);
        dynamicProperties.values().forEach(ReloadableProperty::reload);
        cachedToString = null;
    }

    /**
     * @deprecated Use {@link #loadProperties(String)}
     */
    @Deprecated
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
        LOG.info("[{}] loading config", clientName);
        this.clientName = clientName;
        this.isDynamic = true;
        loadDefaultValues();
        resolver.onChange(this::reload);

        internalProperties.forEach((key, value) -> LOG.info("[{}] {}={}", clientName, key, value.orElse(null)));
    }

    /**
     * @return use {@link #forEach(BiConsumer)}
     */
    @Override
    @Deprecated
    public final Map<String, Object> getProperties() {
        final Map<String, Object> result = new HashMap<>(internalProperties.size());
        forEach((key, value) -> result.put(key.key(), String.valueOf(value)));
        return result;
    }

    @Override
    public void forEach(BiConsumer<IClientConfigKey<?>, Object> consumer) {
        internalProperties.forEach((key, value) -> {
            if (value.isPresent()) {
                consumer.accept(key, value.get());
            }
        });
    }

    /**
     * Register an action that will refresh the cached value for key. Uses the current value as a reference and will
     * update from the dynamic property source to either delete or set a new value.
     *
     * @param key - Property key without client name or namespace
     */
    private <T> void autoRefreshFromPropertyResolver(final IClientConfigKey<T> key) {
        changeActions.computeIfAbsent(key, ignore -> {
            final Supplier<Optional<T>> valueSupplier = () -> resolveFromPropertyResolver(key);
            final Optional<T> current = valueSupplier.get();
            if (current.isPresent()) {
                internalProperties.put(key, current);
            }

            final AtomicReference<Optional<T>> previous = new AtomicReference<>(current);
            return () -> {
                final Optional<T> next = valueSupplier.get();
                if (!next.equals(previous.get())) {
                    LOG.info("[{}] new value for {}: {} -> {}", clientName, key.key(), previous.get(), next);
                    previous.set(next); 
                    internalProperties.put(key, next);
                }
            };
        });
    }

    interface ReloadableProperty<T> extends Property<T> {
        void reload();
    }

    private synchronized <T> Property<T> getOrCreateProperty(final IClientConfigKey<T> key, final Supplier<Optional<T>> valueSupplier, final Supplier<T> defaultSupplier) {
        Preconditions.checkNotNull(valueSupplier, "defaultValueSupplier cannot be null");

        return (Property<T>)dynamicProperties.computeIfAbsent(key, ignore -> new ReloadableProperty<T>() {
                private volatile Optional<T> current = Optional.empty();
                private List<Consumer<T>> consumers = new CopyOnWriteArrayList<>();

                {
                    reload();
                }

                @Override
                public void onChange(Consumer<T> consumer) {
                    consumers.add(consumer);
                }

                @Override
                public Optional<T> get() {
                    return current;
                }

                @Override
                public T getOrDefault() {
                    return current.orElse(defaultSupplier.get());
                }

                @Override
                public void reload() {
                    refreshCounter.incrementAndGet();

                    Optional<T> next = valueSupplier.get();
                    if (!next.equals(current)) {
                        current = next;
                        consumers.forEach(consumer -> consumer.accept(next.orElseGet(defaultSupplier::get)));
                    }
                }

                @Override
                public String toString() {
                    return String.valueOf(get());
                }
            });
    }

    @Override
    public final <T> T get(IClientConfigKey<T> key) {
        Optional<T> value = (Optional<T>)internalProperties.get(key);
        if (value == null) {
            if (!isDynamic) {
                return null;
            } else {
                set(key, null);
                value = (Optional<T>) internalProperties.get(key);
            }
        }

        return value.orElse(null);
    }

    @Override
    public final <T> Property<T> getGlobalProperty(IClientConfigKey<T> key) {
        LOG.debug("[{}] get global property '{}' with default '{}'", clientName, key.key(), key.defaultValue());

        return getOrCreateProperty(
                key,
                () -> resolver.get(key.key(), key.type()),
                key::defaultValue);
    }

    @Override
    public final <T> Property<T> getDynamicProperty(IClientConfigKey<T> key) {
        LOG.debug("[{}] get dynamic property key={} ns={}", clientName, key.key(), getNameSpace());

        get(key);

        return getOrCreateProperty(
                key,
                () -> (Optional<T>)internalProperties.getOrDefault(key, Optional.empty()),
                key::defaultValue);
    }

    @Override
    public <T> Property<T> getPrefixMappedProperty(IClientConfigKey<T> key) {
        LOG.debug("[{}] get dynamic property key={} ns={} client={}", clientName, key.key(), getNameSpace());

        return getOrCreateProperty(
                key,
                getPrefixedMapPropertySupplier(key),
                key::defaultValue);
    }

    /**
     * Resolve a property's final value from the property value.
     * - client scope
     * - default scope
     */
    private <T> Optional<T> resolveFromPropertyResolver(IClientConfigKey<T> key) {
        Optional<T> value;
        if (!StringUtils.isEmpty(clientName)) {
            value = resolver.get(clientName + "." + getNameSpace() + "." + key.key(), key.type());
            if (value.isPresent()) {
                return value;
            }
        }

        return resolver.get(getNameSpace() + "." + key.key(), key.type());
    }

    @Override
    public <T> Optional<T> getIfSet(IClientConfigKey<T> key) {
        return (Optional<T>)internalProperties.getOrDefault(key, Optional.empty());
    }

    private <T> T resolveValueToType(IClientConfigKey<T> key, Object value) {
        if (value == null) {
            return null;
        }

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
                        return PropertyUtils.resolveWithValueOf(type, strValue)
                                .orElseThrow(() -> new IllegalArgumentException("Unsupported value type `" + type + "'"));
                    }
                } else {
                    return PropertyUtils.resolveWithValueOf(type, value.toString())
                            .orElseThrow(() -> new IllegalArgumentException("Incompatible value type `" + value.getClass() + "` while expecting '" + type + "`"));
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Error parsing value '" + value + "' for '" + key.key() + "'", e);
            }
        } else {
            return (T)value;
        }
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

    /**
     * Store the implicit default value for key while giving precedence to default values in the property resolver
     */
    protected final <T> void setDefault(IClientConfigKey<T> key) {
        setDefault(key, key.defaultValue());
    }

    /**
     * Store the default value for key while giving precedence to default values in the property resolver
     */
    protected final <T> void setDefault(IClientConfigKey<T> key, T value) {
        Preconditions.checkArgument(key != null, "key cannot be null");

        value = resolveFromPropertyResolver(key).orElse(value);
        internalProperties.put(key, Optional.ofNullable(value));
        if (isDynamic) {
            autoRefreshFromPropertyResolver(key);
        }
        cachedToString = null;
    }

    @Override
    public <T> IClientConfig set(IClientConfigKey<T> key, T value) {
        Preconditions.checkArgument(key != null, "key cannot be null");

        value = resolveValueToType(key, value);
        if (isDynamic) {
            internalProperties.put(key, Optional.ofNullable(resolveFromPropertyResolver(key).orElse(value)));
            autoRefreshFromPropertyResolver(key);
        } else {
            internalProperties.put(key, Optional.ofNullable(value));
        }
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
        return get(key);
    }

    @Override
    @Deprecated
    public Object getProperty(IClientConfigKey key, Object defaultVal) {
        return Optional.ofNullable(get(key)).orElse(defaultVal);
    }

    @Override
    @Deprecated
    public boolean containsProperty(IClientConfigKey key) {
        return internalProperties.containsKey(key);
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

        override.forEach((key, value) -> setProperty(key, value));

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
        return "ClientConfig:" + internalProperties.entrySet().stream()
                    .map(t -> {
                        if (t.getKey().key().endsWith("Password") && t.getValue().isPresent()) {
                            return t.getKey() + ":***";
                        }
                        return t.getKey() + ":" + t.getValue().orElse(null);
                    })
                .collect(Collectors.joining(", "));
    }
}
