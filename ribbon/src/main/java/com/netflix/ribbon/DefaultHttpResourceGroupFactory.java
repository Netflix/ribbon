package com.netflix.ribbon;

import com.netflix.ribbon.http.HttpResourceGroup;

public class DefaultHttpResourceGroupFactory implements HttpResourceGroupFactory {

    @Override
    public HttpResourceGroup createHttpResourceGroup(String name, ClientOptions options, ClientConfigFactory configFactory, RibbonTransportFactory transportFactory) {
        return new HttpResourceGroup(name, options, configFactory, transportFactory);
    }
}
