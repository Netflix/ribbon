package com.netflix.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.codehaus.jackson.type.TypeReference;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

public class JacksonCodec<T extends Object> implements Serializer<T>, Deserializer<T> {
    
    private static final JacksonCodec instance = new JacksonCodec();
    
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public T deserialize(InputStream in, TypeDef<T> type)
            throws IOException {
        if (String.class.equals(type.getRawType())) {
            return (T) CharStreams.toString(new InputStreamReader(in, Charsets.UTF_8));
        }
        return mapper.readValue(in, new TypeTokenBasedReference<T>(type));
    }
    
    @Override
    public void serialize(OutputStream out, T object, TypeDef<?> type) throws IOException {
        if (type == null) {
            mapper.writeValue(out, object);
        } else {
            ObjectWriter writer = mapper.writerWithType(new TypeTokenBasedReference(type));
            writer.writeValue(out, object);
        }
    }
    
    public static final <T> JacksonCodec<T> getInstance() {
        return instance;
    }
}

class TypeTokenBasedReference<T> extends TypeReference<T> {
    
    final Type type;
    public TypeTokenBasedReference(TypeDef<T> typeToken) {
        type = typeToken.getType();    
        
    }

    @Override
    public Type getType() {
        return type;
    }
}
