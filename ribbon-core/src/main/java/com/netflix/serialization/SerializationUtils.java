package com.netflix.serialization;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.http.HttpHeaders;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

public class SerializationUtils {

    public static <T> T deserializeFromString(Deserializer<T> deserializer, String content, TypeDef<T> typeDef) 
            throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes("UTF-8"));
        return deserializer.deserialize(in, typeDef);
    }
    
    public static <T> T getEntity(HttpResponse response, TypeDef<T> type, Deserializer<T> deserializer) throws IOException {
        return deserializer.deserialize(response.getInputStream(), type);
    }
    
    public static <T> Deserializer<T> getDeserializer(HttpRequest request, HttpHeaders responseHeaders, TypeDef<T> typeDef, SerializationFactory<HttpSerializationContext> serializationFactory) {
        Deserializer<T> deserializer = null;
        if (request.getOverrideConfig() != null) {
            deserializer = request.getOverrideConfig().getTypedProperty(CommonClientConfigKey.Deserializer);
        }
        if (deserializer == null && serializationFactory != null) {
            deserializer = serializationFactory.getDeserializer(new HttpSerializationContext(responseHeaders, request.getUri()), typeDef);
        }
        return deserializer;
    }

}
