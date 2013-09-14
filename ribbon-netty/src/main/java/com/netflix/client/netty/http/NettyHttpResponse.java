package com.netflix.client.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.netflix.client.ClientException;
import com.netflix.client.ResponseWithTypedEntity;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.SerializationFactory;
import com.sun.jersey.api.client.ClientResponse;

public class NettyHttpResponse implements ResponseWithTypedEntity {

    private HttpResponse response;
    private ByteBuf content;
    private SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory;
    private URI requestedURI;
    public NettyHttpResponse(HttpResponse response, ByteBuf content, SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory, URI requestedURI) {
        this.response = response;
        this.content = content;
        this.serializationFactory = serializationFactory;
        this.requestedURI = requestedURI;
    }
    
    @Override
    public <T> T get(Class<T> type) throws ClientException {
        try {
            ContentTypeBasedSerializerKey key = new ContentTypeBasedSerializerKey(response.headers().get(HttpHeaders.CONTENT_TYPE), type);
            Deserializer deserializer = serializationFactory.getDeserializer(key).orNull();
            if (deserializer == null) {
                throw new ClientException("No serializer for " + key);
            }
            if (!content.isReadable()) {
                throw new ClientException("The underlying ByteBuf is not readable");
            }
            int readableBytes = content.readableBytes();
            byte[] raw = new byte[readableBytes];
            if (content.hasArray()) {
                raw = Arrays.copyOfRange(content.array(), content.arrayOffset() + content.readerIndex(), content.arrayOffset() + content.readerIndex() + readableBytes);
            } else {
                content.getBytes(content.readerIndex(), raw);
            }
            content.skipBytes(readableBytes);
            return deserializer.deserialize(raw, type);
        } catch (Throwable e) {
            throw new ClientException("Unable to deserialize the content", e);    
        }
    }

    @Override
    public Object getPayload() throws ClientException {
        return content;
    }

    @Override
    public boolean hasPayload() {
        return content != null;
    }

    @Override
    public URI getRequestedURI() {
        return requestedURI;
    }

    @Override
    public Map<String, Collection<String>> getHeaders() {
        Multimap<String, String> map = ArrayListMultimap.<String, String>create();
        for (Map.Entry<String, String> entry: response.headers().entries()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map.asMap();
    }
    
    @Override
    public boolean isSuccess() {
        return response.getStatus().equals(HttpResponseStatus.OK);
    }

    public int getStatus() {
        return response.getStatus().code();
    }


    public boolean hasEntity() {
        if (content != null && content.isReadable() && content.readableBytes() > 0) {
            return true;
        }
        return false;
    }

}
