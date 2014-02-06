/*
 *
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.client.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCounted;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.netflix.client.ClientException;
import com.netflix.client.ResponseWithTypedEntity;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.SerializationUtils;
import com.netflix.serialization.TypeDef;

class NettyHttpResponse implements ResponseWithTypedEntity, com.netflix.client.http.HttpResponse, ReferenceCounted {

    private final HttpResponse response;
    private final ByteBuf content;
    private final SerializationFactory<HttpSerializationContext> serializationFactory;
    private final HttpRequest request;
    private final IClientConfig requestConfig;
    
    public NettyHttpResponse(HttpResponse response, ByteBuf content, SerializationFactory<HttpSerializationContext> serializationFactory, HttpRequest request, IClientConfig requestConfig) {
        this.response = response;
        this.content = content;
        this.serializationFactory = serializationFactory;
        this.request = request;
        this.requestConfig = requestConfig;
        if (content != null && requestConfig != null 
                && requestConfig.getPropertyAsBoolean(NettyHttpClient.AutoRetainResponse, false)) {
            content.retain();
        }
    }
    
    @Override
    @Deprecated
    public <T> T getEntity(TypeDef<T> typeDef) throws ClientException {
        try {
            Deserializer<T> deserializer = SerializationUtils.getDeserializer(request, requestConfig, new NettyHttpHeaders(response), typeDef, serializationFactory);
            if (deserializer == null) {
                throw new ClientException("No suitable deserializer for type " + typeDef.getRawType());
            }
            return getEntity(typeDef, deserializer);
        } catch (Throwable e) {
            throw new ClientException("Unable to deserialize the content", e);    
        }
    }

    @Override
    @Deprecated
    public <T> T getEntity(Class<T> type) throws ClientException {
        return getEntity(TypeDef.fromClass(type)); 
    }
    
    private InputStream getBytesFromByteBuf() {
        if (content == null) {
            throw new IllegalStateException("There is no content (ByteBuf) from the HttpResponse");
        }
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
        return request.getUri();
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
                e.printStackTrace();
            }
        }
    }

    @Override
    public InputStream getInputStream()  {
        return getBytesFromByteBuf();
    }

    @Override
    public String getStatusLine() {
        return response.getStatus().reasonPhrase();
    }

    @Override
    public com.netflix.client.http.HttpHeaders getHttpHeaders() {
        return new NettyHttpHeaders(response);
    }

    @Override
    public <T> T getEntity(TypeDef<T> type, Deserializer<T> deserializer)
            throws Exception {
        Preconditions.checkNotNull(deserializer);
        return deserializer.deserialize(getInputStream(), type);
    }

    @Override
    public int refCnt() {
        if (content == null) {
            return 0;
        } else {
            return content.refCnt();
        }
    }

    @Override
    public ReferenceCounted retain() {
        if (content != null) {
            content.retain();
        } 
        return this;
    }

    @Override
    public ReferenceCounted retain(int increment) {
        if (content != null) {
            content.retain(increment);
        } 
        return this;
    }

    @Override
    public boolean release() {
        if (content != null) {
            return content.release();
        }
        return true;
    }

    @Override
    public boolean release(int decrement) {
        if (content != null) {
            return content.release(decrement);
        }
        return true;
    }
}
