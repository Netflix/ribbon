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
     * @return Get the current value.  Can be null if not set
     */
    Optional<T> get();

    /**
     * @return Get the current value or the default value if not set
     */
    T getOrDefault();

    default Property<T> fallbackWith(Property<T> fallback) {
        return new FallbackProperty<>(this, fallback);
    }

    static <T> Property<T> of(T value) {
        return new Property<T>() {
            @Override
            public void onChange(Consumer<T> consumer) {
                // It's a static property so no need to track the consumer
            }

            @Override
            public Optional<T> get() {
                return Optional.ofNullable(value);
            }

            @Override
            public T getOrDefault() {
                return value;
            }

            @Override
            public String toString( ){
                return String.valueOf(value);
            }
        };
    }
}
