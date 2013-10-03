package com.netflix.client;

import java.io.IOException;
import java.util.List;

public interface StreamDecoder<E, T> {
    List<E> decode(T input) throws IOException;
}
