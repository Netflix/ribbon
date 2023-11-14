package com.netflix.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class SerializationUtils {

    public static <T> T deserializeFromString(Deserializer<T> deserializer, String content, TypeDef<T> typeDef) 
            throws IOException {
        Objects.requireNonNull(deserializer);
        ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        return deserializer.deserialize(in, typeDef);
    }
    
    public static <T> String serializeToString(Serializer<T> serializer, T obj, TypeDef<?> typeDef) 
            throws IOException {
        return new String(serializeToBytes(serializer, obj, typeDef), StandardCharsets.UTF_8);
    }
    
    public static <T> byte[] serializeToBytes(Serializer<T> serializer, T obj, TypeDef<?> typeDef) 
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializer.serialize(out, obj, typeDef);
        return out.toByteArray();
    }
}
