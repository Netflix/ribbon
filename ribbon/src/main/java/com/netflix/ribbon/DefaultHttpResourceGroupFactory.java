package com.netflix.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.http.HttpResourceGroup;

public class DefaultHttpResourceGroupFactory implements HttpResourceGroupFactory {

    @Override
    public HttpResourceGroup createHttpResourceGroup(String name, ClientOptions options, ClientConfigFactory configFactory, RibbonTransportFactory transportFactory) {
        return new HttpResourceGroup(name, options, configFactory, transportFactory);
    }

    @Override
    public HttpResourceGroup createHttpResourceGroup(String name,
            IClientConfig config, RibbonTransportFactory transportFactory) {
       return new HttpResourceGroup(name, config, transportFactory);
    }
    
    
}
