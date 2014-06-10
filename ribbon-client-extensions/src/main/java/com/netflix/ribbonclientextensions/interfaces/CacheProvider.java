package com.netflix.ribbonclientextensions.interfaces;

public interface CacheProvider<T> {
    T get();
}
