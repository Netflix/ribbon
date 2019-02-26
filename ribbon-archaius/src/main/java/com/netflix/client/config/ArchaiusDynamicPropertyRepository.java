package com.netflix.client.config;

import com.google.common.base.Preconditions;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.PropertyWrapper;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.SubsetConfiguration;

import java.lang.reflect.Type;
import java.util.function.Consumer;

public final class ArchaiusDynamicPropertyRepository implements DynamicPropertyRepository {
    private final Configuration config;

    public ArchaiusDynamicPropertyRepository() {
        this(ConfigurationManager.getConfigInstance());
    }

    public ArchaiusDynamicPropertyRepository(Configuration config) {
        this.config = config;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    private <T> PropertyWrapper<T> getPropertyWrapper(String propName, Class<T> type, T defaultValue) {
        Preconditions.checkArgument(propName != null, "propName may not be null");
        Preconditions.checkArgument(type != null, "type may not be null");

        try {
            if (String.class.equals(type)) {
                return (PropertyWrapper<T>) DynamicPropertyFactory.getInstance().getStringProperty(propName, (String) defaultValue);
            } else if (Integer.class.equals(type)) {
                return (PropertyWrapper<T>) DynamicPropertyFactory.getInstance().getIntProperty(propName, (Integer) defaultValue);
            } else if (Boolean.class.equals(type)) {
                return (PropertyWrapper<T>) DynamicPropertyFactory.getInstance().getBooleanProperty(propName, (Boolean) defaultValue);
            } else if (Double.class.equals(type)) {
                return (PropertyWrapper<T>) DynamicPropertyFactory.getInstance().getDoubleProperty(propName, (Double) defaultValue);
            } else if (Float.class.equals(type)) {
                return (PropertyWrapper<T>) DynamicPropertyFactory.getInstance().getFloatProperty(propName, (Float) defaultValue);
            } else if (Long.class.equals(type)) {
                return (PropertyWrapper<T>) DynamicPropertyFactory.getInstance().getLongProperty(propName, (Long) defaultValue);
            } else {
                throw new UnsupportedOperationException("Dynamic properties for " + type + " not supported");
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Error getting property '%s'", propName), e);
        }
    }
    @Override
    public <T> DynamicProperty<T> getTypedProperty(String key, Type type, T defaultValue) {
        final String propName;
        if (config instanceof SubsetConfiguration) {
            SubsetConfiguration subset = (SubsetConfiguration)config;
            propName = subset.getPrefix() + "." + key;
        } else {
            propName = key;
        }

        final PropertyWrapper<T> propertyWrapper = getPropertyWrapper(propName, (Class)type, defaultValue);

        return new DynamicProperty<T>() {
            @Override
            public void onChange(Consumer<T> consumer) {
                Runnable callback = new Runnable() {
                    @Override
                    public void run() {
                        consumer.accept(propertyWrapper.getValue());
                    }

                    // equals and hashcode needed
                    // since this is anonymous object is later used as a set key

                    @Override
                    public boolean equals(Object other){
                        if (other == null) {
                            return false;
                        }
                        if (getClass() == other.getClass()) {
                            return toString().equals(other.toString());
                        }
                        return false;
                    }

                    @Override
                    public String toString(){
                        return propName;
                    }

                    @Override
                    public int hashCode(){
                        return propName.hashCode();
                    }
                };

                propertyWrapper.addCallback(callback);
            }

            @Override
            public T get() {
                return propertyWrapper.getValue();
            }
        };
    }

    @Override
    public ArchaiusDynamicPropertyRepository withPrefix(String prefix) {
        return new ArchaiusDynamicPropertyRepository(config.subset(prefix));
    }

    @Override
    public void forEachPropertyName(Consumer<String> consumer) {
        config.getKeys().forEachRemaining(consumer);
    }

}
