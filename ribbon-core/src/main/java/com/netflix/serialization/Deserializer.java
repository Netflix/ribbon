package com.netflix.serialization;

import java.io.IOException;
import java.io.InputStream;

public interface Deserializer {
    public <T> T deserialize(byte[] content, Class<T> type) throws IOException;
    
    public <T> T deserialize(InputStream in, Class<T> type) throws IOException;    

}
