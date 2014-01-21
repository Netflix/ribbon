package com.netflix.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;

public class StringDeserializer implements Deserializer<String> {
    
    private static final StringDeserializer instance = new StringDeserializer();
    
    private StringDeserializer() {
    }

    public static final StringDeserializer getInstance() {
        return instance;
    }
    
    @Override
    public String deserialize(InputStream in, TypeDef<String> type)
            throws IOException {
        try {
            String content = CharStreams.toString(new InputStreamReader(in, Charsets.UTF_8));
            return content;
        } finally {
            Closeables.close(in, true);
        }
    }
}
