package com.netflix.ribbonclientextensions;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

public abstract class ResourceGroup<T extends RequestTemplate<?, ?>> {
    private String name;
    private IClientConfig clientConfig;

    public ResourceGroup(String name) {
        this(name, null);
    }

    public ResourceGroup(String name, ClientOptions options) {
        this.name = name;
        clientConfig = loadDefaultConfig(name);
        if (options != null) {
            for (IClientConfigKey key: options.getOptions().keySet()) {
                clientConfig.setPropertyWithType(key, options.getOptions().get(key));
            }
        }
    }
    
    ResourceGroup(IClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }
    
    protected abstract IClientConfig loadDefaultConfig(String name);
    
    protected final IClientConfig getClientConfig() {
        return clientConfig;
    }
    
    public String name() {
        return name;
    }
    
    public abstract <S> T newRequestTemplate(String name, Class<? extends S> classType);
}
