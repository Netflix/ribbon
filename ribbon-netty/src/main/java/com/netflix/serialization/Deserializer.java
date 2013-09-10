package com.netflix.serialization;

import java.io.IOException;

public interface Deserializer {
    public <T> T deserialize(byte[] content, Class<T> type) throws IOException;    
}
