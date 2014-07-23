package com.netflix.client.config;


/**
 * Created by awang on 7/18/14.
 */
public interface ClientConfigFactory {
    IClientConfig newConfig();

    public static class DefaultClientConfigFactory implements ClientConfigFactory {
        @Override
        public IClientConfig newConfig() {
            IClientConfig config = new DefaultClientConfigImpl();
            config.loadDefaultValues();
            return config;
            
        }        
    }
    
    public static final ClientConfigFactory DEFAULT = new DefaultClientConfigFactory();
}
