package com.netflix.client.config;

public class UnboxedIntProperty {
    private volatile int value;

    public UnboxedIntProperty(DynamicProperty<Integer> delegate) {
        this.value = delegate.get();

        delegate.onChange(newValue -> this.value = newValue);
    }

    public int get() {
        return value;
    }
}
