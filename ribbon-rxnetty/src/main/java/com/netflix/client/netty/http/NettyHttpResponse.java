package com.netflix.client.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.google.common.reflect.TypeToken;
import com.netflix.client.ClientException;
import com.netflix.client.ResponseWithTypedEntity;
import com.netflix.client.http.HttpUtils;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.TypeDef;

class NettyHttpResponse implements ResponseWithTypedEntity, com.netflix.client.http.HttpResponse {

    private final HttpResponse response;
    final ByteBuf content;
    private final SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory;
    private final URI requestedURI;
    
    class NettyHttpHeaders implements com.netflix.client.http.HttpHeaders {

        @Override
        public String getFirst(String headerName) {
            return response.headers().get(headerName);
        }

        @Override
        public List<String> getAll(String headerName) {
            return response.headers().getAll(headerName);
        }
        
    }
    
    public NettyHttpResponse(HttpResponse response, ByteBuf content, SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory, URI requestedURI) {
        this.response = response;
        this.content = content;
        this.serializationFactory = serializationFactory;
        this.requestedURI = requestedURI;
    }
    
    @Override
    @Deprecated
    public <T> T getEntity(TypeDef<T> type) throws ClientException {
        try {
            return (T) HttpUtils.getEntity(this, type, this.serializationFactory);
        } catch (Throwable e) {
            throw new ClientException("Unable to deserialize the content", e);    
        }
    }

    @Override
    @Deprecated
    public <T> T getEntity(Class<T> type) throws ClientException {
        try {
            return HttpUtils.getEntity(this, TypeDef.fromClass(type), this.serializationFactory);
        } catch (Throwable e) {
            throw new ClientException("Unable to deserialize the content", e);    
        }
    }
    
    private InputStream getBytesFromByteBuf() {
        return new ByteBufInputStream(content);
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

    @Override
    public void close() {
        if (this.content != null) {
            try {
                while (this.content.refCnt() > 0) {
                    this.content.release();
                }
            } catch (Exception e) { //NOPMD
            }
        }
    }

    @Override
    public InputStream getInputStream()  {
        return getBytesFromByteBuf();
    }

    @Override
    public String getStatusLine() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public com.netflix.client.http.HttpHeaders getHttpHeaders() {
        return new NettyHttpHeaders();
    }
}
