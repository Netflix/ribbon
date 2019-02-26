package com.netflix.client.config;

import java.util.function.Consumer;

/**
 * Ribbon specific constract to abstract from dynamic configuration sources
 * @param <T>
 */
public interface DynamicProperty<T> {
    /**
     * Register a consumer to be called when the configuration changes
     * @param consumer
     */
    void onChange(Consumer<T> consumer);

    /**
     * @return Get the current value or default value
     */
    T get();
}
