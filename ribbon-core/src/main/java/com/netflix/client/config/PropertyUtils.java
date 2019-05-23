package com.netflix.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PropertyUtils {
    private static Logger LOG = LoggerFactory.getLogger(PropertyUtils.class);

    private PropertyUtils() {}

    /**
     * Returns the internal property to the desiredn type
     */
    private static Map<Class<?>, Optional<Method>> valueOfMethods = new ConcurrentHashMap<>();

    public static <T> Optional<T> resolveWithValueOf(Class<T> type, String value) {
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
