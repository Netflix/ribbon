package com.netflix.client.config;

import java.util.Optional;
import java.util.function.Consumer;

public final class FallbackProperty<T> implements Property<T> {
    private final Property<T> primary;
    private final Property<T> fallback;

    public FallbackProperty(Property<T> primary, Property<T> fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public void onChange(Consumer<T> consumer) {
        primary.onChange(ignore -> consumer.accept(getOrDefault()));
        fallback.onChange(ignore -> consumer.accept(getOrDefault()));
    }

    @Override
    public Optional<T> get() {
        Optional<T> value = primary.get();
        if (value.isPresent()) {
            return value;
        }
        return fallback.get();
    }

    @Override
    public T getOrDefault() {
        return primary.get().orElseGet(fallback::getOrDefault);
    }

    @Override
    public String toString() {
        return String.valueOf(get());
    }
}
