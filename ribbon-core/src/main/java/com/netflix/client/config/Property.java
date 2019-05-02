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
     * @return Get the current value.  Can be null if no default value was defined
     */
    T get();

    /**
     * @return Return the value only if not set.  Will return Optional.empty() instead of default value if not set
     */
    default Optional<T> getOptional() { return Optional.ofNullable(get()); }

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
            public T get() {
                return value;
            }

            @Override
            public String toString( ){
                return String.valueOf(value);
            }
        };
    }
}
