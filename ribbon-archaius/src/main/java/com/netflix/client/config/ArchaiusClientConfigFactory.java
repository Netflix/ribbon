package com.netflix.client.config;

public class ArchaiusClientConfigFactory implements ClientConfigFactory {
    @Override
    public IClientConfig newConfig() {
        return new DefaultClientConfigImpl();
    }
}
