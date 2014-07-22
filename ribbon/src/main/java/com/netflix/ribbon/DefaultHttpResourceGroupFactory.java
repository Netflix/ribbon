package com.netflix.ribbon;

import javax.inject.Inject;

import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.RibbonDynamicProxy;

public class DefaultHttpResourceGroupFactory implements HttpResourceGroupFactory {

    private ClientConfigFactory clientConfigFactory;
    private RibbonTransportFactory transportFactory;
        
    @Inject
    public DefaultHttpResourceGroupFactory(ClientConfigFactory clientConfigFactory, RibbonTransportFactory transportFactory) {
        this.clientConfigFactory = clientConfigFactory;
        this.transportFactory = transportFactory;
    }
    
    @Override
    public HttpResourceGroup createHttpResourceGroup(String name, ClientOptions options) {
        return new HttpResourceGroup(name, options, clientConfigFactory, transportFactory);
    }

    @Override
    public HttpResourceGroup createHttpResourceGroup(String name,
            IClientConfig config) {
       return new HttpResourceGroup(name, config, transportFactory);
    }

    @Override
    public <T> T from(Class<T> classType) {
        return RibbonDynamicProxy.newInstance(classType, this, clientConfigFactory, transportFactory);
    }

    @Override
    public <T> T from(Class<T> classType, HttpResourceGroup resourceGroup) {
        return RibbonDynamicProxy.newInstance(classType, resourceGroup);
    }
}
