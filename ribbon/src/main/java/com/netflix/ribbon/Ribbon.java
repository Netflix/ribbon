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
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider;

/**
 * A class that can be used to create {@link com.netflix.ribbon.http.HttpResourceGroup}, {@link com.netflix.ribbon.http.HttpResourceGroup.Builder},
 * and dynamic proxy of service interfaces. It delegates to a default {@link com.netflix.ribbon.RibbonResourceFactory} to do the work.
 * For better configurability or in DI enabled application, it is recommended to use {@link com.netflix.ribbon.RibbonResourceFactory} directly.
 *
 */
public final class Ribbon {
    private static final RibbonResourceFactory factory = new DefaultResourceFactory(ClientConfigFactory.DEFAULT, RibbonTransportFactory.DEFAULT, AnnotationProcessorsProvider.DEFAULT);
    
    private Ribbon() {
    }

    /**
     * Create the {@link com.netflix.ribbon.http.HttpResourceGroup.Builder} with a name, where further options can be set to
     * build the {@link com.netflix.ribbon.http.HttpResourceGroup}.
     *
     * @param name name of the resource group, as well as the transport client that will be created once
     *             the HttpResourceGroup is built
     */
    public static Builder createHttpResourceGroupBuilder(String name) {
        return factory.createHttpResourceGroupBuilder(name);
    }

    /**
     * Create the {@link com.netflix.ribbon.http.HttpResourceGroup} with a name.
     *
     * @param name name of the resource group, as well as the transport client that will be created once
     *             the HttpResourceGroup is built
     */
    public static HttpResourceGroup createHttpResourceGroup(String name) {
        return factory.createHttpResourceGroup(name);
    }

    /**
     * Create the {@link com.netflix.ribbon.http.HttpResourceGroup} with a name.
     *
     * @param name name of the resource group, as well as the transport client
     * @param options Options to override the client configuration created
     */
    public static HttpResourceGroup createHttpResourceGroup(String name, ClientOptions options) {
        return factory.createHttpResourceGroup(name, options);
    }

    /**
     * Create an instance of remote service interface.
     *
     * @param contract interface class of the remote service
     *
     * @param <T> Type of the instance
     */
    public static <T> T from(Class<T> contract) {
        return factory.from(contract);
    }
}
