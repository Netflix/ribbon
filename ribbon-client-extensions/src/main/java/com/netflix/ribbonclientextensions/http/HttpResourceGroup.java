package com.netflix.ribbonclientextensions.http;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;

import com.netflix.client.config.ClientConfigBuilder;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.netty.RibbonTransport;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.ribbonclientextensions.ClientOptions;
import com.netflix.ribbonclientextensions.RequestTemplate;
import com.netflix.ribbonclientextensions.ResourceGroup;

public class HttpResourceGroup extends ResourceGroup<HttpRequestTemplate<?>> {
    private final HttpClient<ByteBuf, ByteBuf> client;
    
    public HttpResourceGroup(String groupName) {
        this(groupName, null);
    }
    
    public HttpResourceGroup(String groupName, ClientOptions options) {
        super(groupName, options);
        client = RibbonTransport.newHttpClient(getClientConfig());
    }
    
    protected IClientConfig loadDefaultConfig(String groupName) {
        return ClientConfigBuilder.newBuilderWithArchaiusProperties(groupName).build();
    }
    
    public HttpResourceGroup withCommonHeader(String name, String value) {
        return this;
    }

    @Override
    public <T> HttpRequestTemplate<T> newRequestTemplate(String name,
            Class<? extends T> classType) {
        return new HttpRequestTemplate<T>(name, HttpResourceGroup.this, client, classType);
    }
    
    public HttpRequestTemplate<ByteBuf> newRequestTemplate(String name) {
        return newRequestTemplate(name, ByteBuf.class);
    }
    
}
