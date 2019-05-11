package com.netflix.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal abstraction to decouple the property source from Ribbon's internal configuration.
 */
public interface PropertyResolver {
    Logger LOG = LoggerFactory.getLogger(PropertyResolver.class);

    /**
     * @param key
     * @param type
     * @param <T>
     * @return Get the value of a property or Optional.empty() if not set
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * Provide action to invoke when config changes
     * @param action
     */
    void onChange(Runnable action);

    /**
     * Returns the internal property to the desiredn type
     */
    Map<Class<?>, Optional<Method>> valueOfMethods = new ConcurrentHashMap<>();

    static <T> Optional<T> resolveWithValueOf(Class<T> type, String value) {
        return valueOfMethods.computeIfAbsent(type, ignore -> {
            try {
                return Optional.of(type.getDeclaredMethod("valueOf", String.class));
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            } catch (Exception e) {
                LOG.warn("Unable to determine if type " + type + " has a valueOf() static method", e);
                return Optional.empty();
            }
        }).map(method -> {
            try {
                return (T)method.invoke(null, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}
