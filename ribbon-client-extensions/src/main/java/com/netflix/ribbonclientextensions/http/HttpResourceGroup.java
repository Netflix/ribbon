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
import com.netflix.ribbonclientextensions.ResourceGroup;

public class HttpResourceGroup implements ResourceGroup {

    private String groupName;
    private IClientConfig config;
    private ILoadBalancer loadBalancer;
    
    public class HttpRequestTemplateBuilder extends RequestTemplateBuilder<HttpRequestTemplate<?>>{
        private HttpClient<ByteBuf, ByteBuf> client;
        HttpRequestTemplateBuilder() {
            if (loadBalancer == null) {
                client = RibbonTransport.newHttpClient(config);
            } else {
                client = RibbonTransport.newHttpClient(loadBalancer, config);
            }
        }
        @Override
        public <T> HttpRequestTemplate<T> newRequestTemplate(String name, Class<? extends T> type) {
            return new HttpRequestTemplate<T>(name, HttpResourceGroup.this, client, type);
        }
        
        public HttpRequestTemplate<ByteBuf> newRequestTemplate(String name) {
            return newRequestTemplate(name, ByteBuf.class);
        }
        
    }
    
    @Override
    public String name() {
        return groupName;
    }
    
    public HttpResourceGroup(String groupName) {
        this.groupName = groupName;
        config = getDefaultConfig(groupName);
    }
    
    protected IClientConfig getDefaultConfig(String groupName) {
        return ClientConfigBuilder.newBuilderWithArchaiusProperties(groupName).build();
    }
    
    public HttpResourceGroup withCommonHeader(String name, String value) {
        return this;
    }
    
    @SuppressWarnings({"rawtypes", "unchecked"}) 
    @Override
    public HttpResourceGroup withClientConfig(IClientConfig overrideConfig) {
        if (config instanceof DefaultClientConfigImpl) {
            ((DefaultClientConfigImpl) config).applyOverride(overrideConfig);
        } else {
            for (IClientConfigKey key: CommonClientConfigKey.keys()) {                
                Object value = overrideConfig.getPropertyWithType(key);
                if (value != null) {
                    config.setPropertyWithType(key, value);
                }
            }
        }
        return this;
    }
    
    public HttpResourceGroup withLoadBalancer(ILoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        return this;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public HttpRequestTemplateBuilder requestTemplateBuilder() {
        return new HttpRequestTemplateBuilder();
    }
}
