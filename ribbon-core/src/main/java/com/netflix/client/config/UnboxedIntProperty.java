package com.netflix.client.config;

public class UnboxedIntProperty {
    private volatile int value;

    public UnboxedIntProperty(Property<Integer> delegate) {
        this.value = delegate.getOrDefault();

        delegate.onChange(newValue -> this.value = newValue);
    }

    public UnboxedIntProperty(int constantValue) {
        this.value = constantValue;
    }

    public int get() {
        return value;
    }
}
