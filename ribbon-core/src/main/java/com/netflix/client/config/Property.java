package com.netflix.client.config;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Ribbon specific encapsulation of a dynamic configuration property
 * @param <T>
 */
public interface Property<T> {
    /**
     * Register a consumer to be called when the configuration changes
     * @param consumer
     */
    void onChange(Consumer<T> consumer);

    /**
     * @return Get the current value or default value
     */
    T get();

    default Optional<T> getOptional() { return Optional.ofNullable(get()); }

    default Property<T> fallbackWith(Property<T> fallback) {
        return new FallbackProperty<>(this, fallback);
    }

    static <T> Property<T> of(T value) {
        return new Property<T>() {
            @Override
            public void onChange(Consumer<T> consumer) {

            }

            @Override
            public T get() {
                return value;
            }
        };
    }
}
