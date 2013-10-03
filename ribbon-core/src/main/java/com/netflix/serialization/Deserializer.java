package com.netflix.serialization;

import java.io.IOException;
import java.io.InputStream;

import com.google.common.reflect.TypeToken;

public interface Deserializer {
    public <T> T deserialize(byte[] content, Class<T> type) throws IOException;
    
    public <T> T deserialize(InputStream in, Class<T> type) throws IOException;

    public String deserializeAsString(byte[] content) throws IOException;
    
    public String deserializeAsString(InputStream in) throws IOException;

    
    public <T> T deserialize(byte[] content, TypeToken<T> typeToken) throws IOException;
    
    public <T> T deserialize(InputStream in, TypeToken<T> type) throws IOException;    


}
