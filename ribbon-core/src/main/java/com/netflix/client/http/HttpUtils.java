package com.netflix.client.http;

import java.io.IOException;

import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.TypeDef;

public class HttpUtils {

    public static <T> T getEntity(HttpResponse response, TypeDef<T> type, Deserializer deserializer) throws IOException {
        return deserializer.deserialize(response.getInputStream(), type);
    }
    
    public static <T> T getEntity(HttpResponse response, TypeDef<T> type, 
            SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory) throws IOException {
        String contentType = response.getHttpHeaders().getFirst("Content-Type");
        Deserializer deserializer = serializationFactory.getDeserializer(new ContentTypeBasedSerializerKey(contentType, type));
        return deserializer.deserialize(response.getInputStream(), type);
    }
}
