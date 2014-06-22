package com.netflix.ribbonclientextensions;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

public interface ResourceGroup {
    String name();

    ResourceGroup withLoadBalancer(ILoadBalancer loadBalancer);
    
    ResourceGroup withClientConfig(IClientConfig config);    
    
    <T extends RequestTemplate<?, ?>> RequestTemplateBuilder<T> requestTemplateBuilder();
    
    public abstract class RequestTemplateBuilder<T extends RequestTemplate<?, ?>> {
        public abstract <S> T newRequestTemplate(String name, Class<? extends S> classType);
    }
}
