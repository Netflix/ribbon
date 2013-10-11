package com.netflix.httpasyncclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.netflix.client.ClientException;
import com.netflix.client.ClientException.ErrorType;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.SerializationFactory;

class HttpClientResponse implements com.netflix.client.http.HttpResponse {

    private SerializationFactory<ContentTypeBasedSerializerKey>  factory;
    private HttpResponse response;
    private URI requestedURI;
    private AbstractAsyncResponseConsumer<HttpResponse> consumer;
    
    public HttpClientResponse(HttpResponse response, SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory, URI requestedURI, AbstractAsyncResponseConsumer<HttpResponse> consumer) {
        this.response = response;    
        this.factory = serializationFactory;
        this.requestedURI = requestedURI;
        this.consumer = consumer;
    }
    
    @Override
    public Object getPayload() throws ClientException {
        return response.getEntity();
    }

    @Override
    public boolean isSuccess() {
        return response.getStatusLine().getStatusCode() == 200;
    }

    @Override
    public URI getRequestedURI() {
        return this.requestedURI;
    }

    @Override
    public Map<String, Collection<String>> getHeaders() {
        Multimap<String, String> map = ArrayListMultimap.create();
        for (Header header: response.getAllHeaders()) {
            map.put(header.getName(), header.getValue());
        }
        return map.asMap();
        
    }
    
    public int getStatus() {
        return response.getStatusLine().getStatusCode();
    }
    
    
    @Override
    public boolean hasPayload() {
        HttpEntity entity = response.getEntity();
        try {
            return (entity != null && entity.getContent() != null && entity.getContent().available() > 0);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public <T> T getEntity(Class<T> type) throws ClientException {
        return getEntity(TypeToken.of(type));
    }

    @Override
    public <T> T getEntity(TypeToken<T> type) throws ClientException {
        ContentTypeBasedSerializerKey key = new ContentTypeBasedSerializerKey(response.getFirstHeader("Content-type").getValue(), type);
        Deserializer deserializer = factory.getDeserializer(key).orNull();
        try {
            return deserializer.deserialize(response.getEntity().getContent(), type);
        } catch (IOException e) {
            throw new ClientException(e);
        }

    }
    
    @Override
    public boolean hasEntity() {
        return hasPayload();
    }

    @Override
    public InputStream getInputStream() throws ClientException {
        try {
            return response.getEntity().getContent();
        } catch (Exception e) {
            throw new ClientException(ErrorType.GENERAL, "Unable to get InputStream", e);
        }
    }

    @Override
    public void close() throws IOException {
        consumer.close();
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            try {
                entity.getContent().close();
            } catch (IllegalStateException e) { // NOPMD
            }
        }    
        
    }
    
}