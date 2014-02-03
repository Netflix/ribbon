/*
 *
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.httpasyncclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.netflix.client.ClientException;
import com.netflix.client.ClientException.ErrorType;
import com.netflix.client.http.HttpHeaders;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.TypeDef;

class HttpClientResponse implements com.netflix.client.http.HttpResponse {

    private List<? extends SerializationFactory<HttpSerializationContext>> factory;
    private HttpResponse response;
    private URI requestedURI;
    private AbstractAsyncResponseConsumer<HttpResponse> consumer;
    
    public HttpClientResponse(HttpResponse response, List<? extends SerializationFactory<HttpSerializationContext>> serializationFactory, URI requestedURI, AbstractAsyncResponseConsumer<HttpResponse> consumer) {
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
        return getEntity(TypeDef.fromClass(type));
    }

    @Override
    public <T> T getEntity(TypeDef<T> type) throws ClientException {
        HttpSerializationContext key = new HttpSerializationContext(getHttpHeaders(), getRequestedURI());
        for (SerializationFactory<HttpSerializationContext> f: factory) {
            Deserializer<T> deserializer = f.getDeserializer(key, type);
            if (deserializer != null) {
                try {
                    return getEntity(type, deserializer);
                } catch (Exception e) {
                    throw new ClientException(e);
                }
            }
        }
        throw new ClientException("No suitable deserializer for " + key);
    }
    
    @Override
    public boolean hasEntity() {
        return hasPayload();
    }

    @Override
    public InputStream getInputStream() {
        try {
            return response.getEntity().getContent();
        } catch (Exception e) {
            throw new RuntimeException("Unable to get InputStream", e);
        }
    }

    @Override
    public void close() {
        try {
            consumer.close();
        } catch (Exception e) { // NOPMD
        }
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            try {
                entity.getContent().close();
            } catch (Exception e) { // NOPMD
            } 
        }    
        
    }

    @Override
    public String getStatusLine() {
        return response.getStatusLine().toString();
    }

    @Override
    public HttpHeaders getHttpHeaders() {
        return new HttpHeaders() {
            @Override
            public String getFirstValue(String headerName) {
                return response.getFirstHeader(headerName).getValue();
            }

            @Override
            public List<String> getAllValues(String headerName) {
                List<String> values = null;
                Header[] headers = response.getHeaders(headerName);
                if (headers != null) {
                    values = Lists.newArrayList();
                    for (Header header: headers) {
                        values.add(header.getValue());
                    }
                }
                return values;
            }

            @Override
            public List<Entry<String, String>> getAllHeaders() {
                List<Entry<String, String>> all = Lists.newLinkedList();
                for (Header header: response.getAllHeaders()) {
                    all.add(new AbstractMap.SimpleEntry<String, String>(header.getName(), header.getValue()));
                }
                return all;
            }

            @Override
            public boolean containsHeader(String name) {
                return response.containsHeader(name);
            }
        };
    }

    @Override
    public <T> T getEntity(TypeDef<T> type, Deserializer<T> deserializer)
            throws Exception {
        return deserializer.deserialize(response.getEntity().getContent(), type);
    }
}
