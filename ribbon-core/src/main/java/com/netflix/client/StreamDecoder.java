package com.netflix.client;

import java.io.IOException;

public interface StreamDecoder<T, S> {
    T decode(S input) throws IOException;
}
