package com.netflix.serialization;

import java.util.List;

public interface StreamDecoder<T, S> {
    List<S> decode(T input); 
}
