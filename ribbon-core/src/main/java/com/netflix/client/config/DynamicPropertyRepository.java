package com.netflix.client.config;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

/**
 * Ribbon specific abstraction to a Repository of dynamic configuration properties
 */
public interface DynamicPropertyRepository {
    DynamicPropertyRepository DEFAULT = findDynamicPropertyRepository();

    /**
     * Proprity of the repository when loading from the server loader.  The highest priority wins
     * @return
     */
    int getPriority();

    /**
     * Look for a static DynamicPropertyRepository via the service loader.  It's OK to have an empty repository for
     * situations where one is provided via dependency injection.
     * @return
     */
    static DynamicPropertyRepository findDynamicPropertyRepository() {
        return StreamSupport.stream(ServiceLoader.load(DynamicPropertyRepository.class).spliterator(), false)
                .sorted(Comparator
                        .comparingInt(DynamicPropertyRepository::getPriority)
                        .thenComparing(Comparator.comparing(f -> f.getClass().getCanonicalName())))
                .findFirst()
                .orElse(null);
    }

    /**
     * Return a DynamicProperty for the names key and type.  Will return the specified default value if not found.
     * @param key
     * @param type
     * @param defaultValue
     * @param <T>
     * @return
     */
    default <T> DynamicProperty<T> getProperty(String key, Class<T> type, T defaultValue) {
        return getTypedProperty(key, type, defaultValue);
    }

    <T> DynamicProperty<T> getTypedProperty(String key, Type type, T defaultValue);

    /**
     * Return a new {@link DynamicPropertyRepository} containing only properties with the specified prefix
     * @param prefix
     * @return
     */
    DynamicPropertyRepository withPrefix(String prefix);

    /**
     * Invoked the provided consumer for every property name
     * @param consumer
     */
    void forEachPropertyName(Consumer<String> consumer);
}
