package com.netflix.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.RibbonDynamicProxy;

/**
 * Factory for creating an HttpResourceGroup.  For DI either bind DefaultHttpResourceGroupFactory
 * or implement your own to customize or override HttpResourceGroup.
 * 
 * @author elandau
 */
public interface RibbonResourceFactory {
    HttpResourceGroup createHttpResourceGroup(String name, ClientOptions options);
    
    HttpResourceGroup createHttpResourceGroup(String name, IClientConfig config);
    
    <T> T from(Class<T> classType);
    
    <T> T from(Class<T> classType, HttpResourceGroup resourceGroup);
}
