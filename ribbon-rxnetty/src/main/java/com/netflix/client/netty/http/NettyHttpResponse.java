package com.netflix.client.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.netflix.client.ClientException;
import com.netflix.client.ResponseWithTypedEntity;
import com.netflix.client.http.HttpRequest;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.SerializationUtils;
import com.netflix.serialization.TypeDef;

class NettyHttpResponse implements ResponseWithTypedEntity, com.netflix.client.http.HttpResponse {

    private final HttpResponse response;
    final ByteBuf content;
    private final SerializationFactory<HttpSerializationContext> serializationFactory;
    private final HttpRequest request;
    
    public NettyHttpResponse(HttpResponse response, ByteBuf content, SerializationFactory<HttpSerializationContext> serializationFactory, HttpRequest request) {
        this.response = response;
        this.content = content;
        if (content != null) {
            content.retain();
        }
        this.serializationFactory = serializationFactory;
        this.request = request;
    }
    
    @Override
    @Deprecated
    public <T> T getEntity(TypeDef<T> typeDef) throws ClientException {
        try {
            Deserializer<T> deserializer = SerializationUtils.getDeserializer(request, new NettyHttpHeaders(response), typeDef, serializationFactory);
            if (deserializer == null) {
                throw new ClientException("No suitable deserializer for type " + typeDef.getRawType());
            }
            return (T) SerializationUtils.getEntity(this, typeDef, deserializer);
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
}
