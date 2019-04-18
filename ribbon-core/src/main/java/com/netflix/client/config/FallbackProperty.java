package com.netflix.client.config;

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
        primary.onChange(ignore -> consumer.accept(get()));
        fallback.onChange(ignore -> consumer.accept(get()));
    }

    @Override
    public T get() {
        return primary.getOptional().orElseGet(fallback::get);
    }
}
