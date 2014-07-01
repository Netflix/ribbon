package com.netflix.ribbonclientextensions.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.reactivex.netty.protocol.http.client.HttpClient;

import com.netflix.client.config.ClientConfigBuilder;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.RibbonTransport;
import com.netflix.ribbonclientextensions.ClientOptions;
import com.netflix.ribbonclientextensions.ResourceGroup;

public class HttpResourceGroup extends ResourceGroup<HttpRequestTemplate<?>> {
    private final HttpClient<ByteBuf, ByteBuf> client;
    private final HttpHeaders headers;
    
    public HttpResourceGroup(String groupName) {
        this(groupName, null);
    }
    
    public HttpResourceGroup(String groupName, ClientOptions options) {
        super(groupName, options);
        client = RibbonTransport.newHttpClient(getClientConfig());
        headers = new DefaultHttpHeaders();
    }
    
    protected IClientConfig loadDefaultConfig(String groupName) {
        return ClientConfigBuilder.newBuilderWithArchaiusProperties(groupName).build();
    }
    
    public HttpResourceGroup withCommonHeader(String name, String value) {
        headers.add(name, value);
        return this;
    }

    @Override
    public <T> HttpRequestTemplate<T> newRequestTemplate(String name,
            Class<? extends T> classType) {
        return new HttpRequestTemplate<T>(name, this, classType);
    }
    
    public HttpRequestTemplate<ByteBuf> newRequestTemplate(String name) {
        return newRequestTemplate(name, ByteBuf.class);
    }
    
    HttpHeaders getHeaders() {
        return headers;
    }
    
    public HttpClient<ByteBuf, ByteBuf> getClient() {
        return client;
    }
}
