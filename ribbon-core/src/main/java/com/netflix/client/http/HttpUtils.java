package com.netflix.client.http;

import java.io.IOException;

import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.TypeDef;

public class HttpUtils {

    public static <T> T getEntity(HttpResponse response, TypeDef<T> type, Deserializer<T> deserializer) throws IOException {
        return deserializer.deserialize(response.getInputStream(), type);
    }
    
    public static <T> T getEntity(HttpResponse response, TypeDef<T> type, 
            SerializationFactory<HttpSerializationContext> serializationFactory) throws IOException {
        Deserializer<T> deserializer = serializationFactory.getDeserializer(new HttpSerializationContext(response.getHttpHeaders(), response.getRequestedURI()), type);
        return deserializer.deserialize(response.getInputStream(), type);
    }
}
