package com.netflix.serialization;

import java.io.IOException;
import java.io.OutputStream;

public interface Serializer {
    public byte[] serialize(Object object) throws IOException;
    
    public void serialize(OutputStream out, Object object) throws IOException;    

}
