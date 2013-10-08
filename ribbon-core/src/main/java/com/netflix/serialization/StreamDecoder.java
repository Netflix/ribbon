package com.netflix.serialization;

public interface StreamDecoder<T, S> {
    S decode(T input); 
}
