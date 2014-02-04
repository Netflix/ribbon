package com.netflix.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpHeaders;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

public class SerializationUtils {

    public static <T> T deserializeFromString(Deserializer<T> deserializer, String content, TypeDef<T> typeDef) 
            throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes("UTF-8"));
        return deserializer.deserialize(in, typeDef);
    }
    
    public static <T> String serializeToString(Serializer<T> serializer, T obj, TypeDef<?> typeDef) 
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializer.serialize(out, obj, typeDef);
        return new String(out.toByteArray(), "UTF-8");
    }
        
    public static <T> Deserializer<T> getDeserializer(HttpRequest request, IClientConfig requestConfig, HttpHeaders responseHeaders, TypeDef<T> typeDef, 
            SerializationFactory<HttpSerializationContext> serializationFactory) {
        Deserializer<T> deserializer = null;
        IClientConfig config = (requestConfig == null) ? request.getOverrideConfig() : requestConfig;
        if (config != null) {
         deserializer = (Deserializer<T>) config.getPropertyWithType(CommonClientConfigKey.Deserializer);
        }
        if (deserializer == null && serializationFactory != null) {
            deserializer = serializationFactory.getDeserializer(new HttpSerializationContext(responseHeaders, request.getUri()), typeDef);
        }
        if (deserializer == null && typeDef.getRawType().equals(String.class)) {
            return (Deserializer<T>) StringDeserializer.getInstance();
        }
        return deserializer;
    }
}
