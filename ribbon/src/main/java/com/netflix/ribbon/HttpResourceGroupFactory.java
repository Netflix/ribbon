package com.netflix.ribbon;

import com.netflix.ribbon.http.HttpResourceGroup;

/**
 * Factory for creating an HttpResourceGroup.  For DI either bind DefaultHttpResourceGroupFactory
 * or implement your own to customize or override HttpResourceGroup.
 * 
 * @author elandau
 */
public interface HttpResourceGroupFactory {

    HttpResourceGroup createHttpResourceGroup(String name);

    HttpResourceGroup createHttpResourceGroup(String name, ClientOptions options);

}
