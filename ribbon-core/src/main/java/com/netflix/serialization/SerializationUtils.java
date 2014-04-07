package com.netflix.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.google.common.base.Preconditions;

public class SerializationUtils {

    public static <T> T deserializeFromString(Deserializer<T> deserializer, String content, TypeDef<T> typeDef) 
            throws IOException {
        Preconditions.checkNotNull(deserializer);
        ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes("UTF-8"));
        return deserializer.deserialize(in, typeDef);
    }
    
    public static <T> String serializeToString(Serializer<T> serializer, T obj, TypeDef<?> typeDef) 
            throws IOException {
        return new String(serializeToBytes(serializer, obj, typeDef), "UTF-8");
    }
    
    public static <T> byte[] serializeToBytes(Serializer<T> serializer, T obj, TypeDef<?> typeDef) 
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializer.serialize(out, obj, typeDef);
        return out.toByteArray();
    }
}
