package com.netflix.serialization;

import java.io.IOException;
import java.io.InputStream;

import com.google.common.reflect.TypeToken;

public interface Deserializer {
    public <T> T deserialize(InputStream in, TypeToken<T> type) throws IOException;    
}
