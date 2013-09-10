package com.netflix.serialization;

import java.io.IOException;

public interface Serializer {
    public byte[] serialize(Object object) throws IOException;    
}
