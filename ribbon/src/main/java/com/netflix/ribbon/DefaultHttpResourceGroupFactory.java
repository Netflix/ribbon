package com.netflix.ribbon;

import com.netflix.ribbon.http.HttpResourceGroup;

public class DefaultHttpResourceGroupFactory implements HttpResourceGroupFactory {
    @Override
    public HttpResourceGroup createHttpResourceGroup(String name) {
        return new HttpResourceGroup(name);
    }

    @Override
    public HttpResourceGroup createHttpResourceGroup(String name, ClientOptions options) {
        return new HttpResourceGroup(name, options);
    }

}
