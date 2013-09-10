package com.netflix.serialization;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;

import com.google.common.base.Optional;

public class DefaultSerializationFactory implements SerializationFactory<ContentTypeBasedSerializerKey>{

    private static final JsonCodec instance = new JsonCodec();
    @Override
    public Optional<Deserializer> getDeserializer(ContentTypeBasedSerializerKey key) {
        if (key.getContentType().equalsIgnoreCase("application/json")) {
            return Optional.<Deserializer>of(instance);
        }
        return Optional.absent();
    }

    @Override
    public Optional<Serializer> getSerializer(ContentTypeBasedSerializerKey key) {
        if (key.getContentType().equalsIgnoreCase("application/json")) {
            return Optional.<Serializer>of(instance);
        }
        return Optional.absent();
    }

}

class JsonCodec implements Serializer, Deserializer {

    private ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public <T> T deserialize(byte[] content, Class<T> type) throws IOException {
        return mapper.readValue(content, type);
    }

    @Override
    public byte[] serialize(Object object) throws IOException {
        return mapper.writeValueAsBytes(object);
    }
    
}