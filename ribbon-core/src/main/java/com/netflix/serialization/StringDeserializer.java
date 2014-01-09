package com.netflix.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;

public class StringDeserializer implements Deserializer {
    @Override
    public <T> T deserialize(InputStream in, TypeDef<T> type)
            throws IOException {
        if (type.getRawType().isAssignableFrom(String.class)) {
            String content = CharStreams.toString(new InputStreamReader(in, Charsets.UTF_8));
            Closeables.close(in, true);
            return (T) content;
        } else {
            throw new IllegalArgumentException("StringDeserializer cannot deserialize InputStream into type " + type.getRawType());
        }
    }
}
