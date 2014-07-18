package com.netflix.ribbon;

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;

/**
 * Created by awang on 7/18/14.
 */
public interface ClientConfigFactory {
    IClientConfig newConfig();

    public static final ClientConfigFactory DEFAULT = new ClientConfigFactory() {
        @Override
        public IClientConfig newConfig() {
            return new DefaultClientConfigImpl();
        }
    };
}
