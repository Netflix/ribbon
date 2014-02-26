package com.netflix.ribbon.examples.netty.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.netflix.serialization.Deserializer;
import com.netflix.serialization.Serializer;
import com.netflix.serialization.TypeDef;
import com.thoughtworks.xstream.XStream;

public class XmlCodec<T extends Object> implements Serializer<T>, Deserializer<T> {

    static XStream xstream = new XStream();
    
    static XmlCodec instance = new XmlCodec();
    
    private XmlCodec() {
    }
    
    public static <T> XmlCodec<T> getInstance() {
        return instance;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(InputStream in, TypeDef<T> type)
            throws IOException {
        System.out.println("Deserializing using XStream");
        return (T) xstream.fromXML(in);
    }

    @Override
    public void serialize(OutputStream out, T object, TypeDef<?> type) throws IOException {
        xstream.toXML(object, out);
    }

}

