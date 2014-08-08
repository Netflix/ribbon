/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon;

import com.netflix.client.config.ClientConfigFactory;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.http.HttpResourceGroup.Builder;
import com.netflix.ribbon.proxy.RibbonDynamicProxy;

/**
 * Factory for creating an HttpResourceGroup.  For DI either bind DefaultHttpResourceGroupFactory
 * or implement your own to customize or override HttpResourceGroup.
 * 
 * @author elandau
 */
public abstract class RibbonResourceFactory {
    protected final ClientConfigFactory clientConfigFactory;
    protected final RibbonTransportFactory transportFactory;

    public static final RibbonResourceFactory DEFAULT = new DefaultResourceFactory(ClientConfigFactory.DEFAULT, RibbonTransportFactory.DEFAULT);

    public RibbonResourceFactory(ClientConfigFactory configFactory, RibbonTransportFactory transportFactory) {
        this.clientConfigFactory = configFactory;
        this.transportFactory = transportFactory;
    }

    public Builder createHttpResourceGroupBuilder(String name) {
        Builder builder = HttpResourceGroup.Builder.newBuilder(name, clientConfigFactory, transportFactory);
        return builder;
    }
    
    public <T> T from(Class<T> classType) {
        return RibbonDynamicProxy.newInstance(classType, this, clientConfigFactory, transportFactory);
    }

    public Builder createHttpResourceGroup(String name, ClientOptions options) {
        Builder builder = Builder.newBuilder(name, clientConfigFactory, transportFactory);
        builder.withClientOptions(options);
        return builder;
    }
}
