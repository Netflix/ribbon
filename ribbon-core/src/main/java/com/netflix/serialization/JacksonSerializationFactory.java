package com.netflix.serialization;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.CharBuffer;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.CharStreams;
import com.google.common.reflect.TypeToken;

public class JacksonSerializationFactory implements SerializationFactory<ContentTypeBasedSerializerKey>{

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
    /*
    @Override
    public <T> T deserialize(byte[] content, Class<T> type) throws IOException {
        return mapper.readValue(content, type);
    }

    @Override
    public byte[] serialize(Object object) throws IOException {
        return mapper.writeValueAsBytes(object);
    }

    @Override
    public <T> T deserialize(InputStream in, Class<T> type) throws IOException {
        return mapper.readValue(in, type);
    }

    @Override
    public void serialize(OutputStream out, Object object) throws IOException {
        mapper.writeValue(out, object);
    }

    @Override
    public <T> T deserialize(byte[] content, TypeToken<T> type)
            throws IOException {
        return mapper.readValue(content, new TypeTokenBasedReference<T>(type));
    }
    */
    @Override
    public <T> T deserialize(InputStream in, TypeToken<T> type)
            throws IOException {
        return mapper.readValue(in, new TypeTokenBasedReference<T>(type));
    }
    
    @Override
    public byte[] serialize(Object object) throws IOException {
        return mapper.writeValueAsBytes(object);
    }
    /*
    @Override
    public void serialize(OutputStream out, Object object) throws IOException {
        mapper.writeValue(out, object);
    }
    */
    /*
    @Override
    public String deserializeAsString(byte[] content) throws IOException {
        return new String(content, Charsets.UTF_8);
    }

    @Override
    public String deserializeAsString(InputStream in) throws IOException {
        String content = CharStreams.toString(new InputStreamReader(in, Charsets.UTF_8));
        return content;
    } */
}

class TypeTokenBasedReference<T> extends TypeReference<T> {
    
    final Type type;
    public TypeTokenBasedReference(TypeToken<T> typeToken) {
        type = typeToken.getType();    
        
    }

    @Override
    public Type getType() {
        return type;
    }
}
