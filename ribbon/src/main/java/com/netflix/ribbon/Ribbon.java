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

import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.RibbonDynamicProxy;

public final class Ribbon {
    private static final HttpResourceGroupFactory factory = new DefaultHttpResourceGroupFactory();
    
    private Ribbon() {
    }

    public static HttpResourceGroup createHttpResourceGroup(String name) {
        return factory.createHttpResourceGroup(name, ClientOptions.create(), ClientConfigFactory.DEFAULT, RibbonTransportFactory.DEFAULT);
    }

    public static HttpResourceGroup createHttpResourceGroup(String name, ClientOptions options) {
        return factory.createHttpResourceGroup(name, options, ClientConfigFactory.DEFAULT, RibbonTransportFactory.DEFAULT);
    }

    public static <T> T from(Class<T> contract) {
        return RibbonDynamicProxy.newInstance(contract, null);
    }

    public static <T> T from(Class<T> contract, HttpResourceGroup httpResourceGroup) {
        return RibbonDynamicProxy.newInstance(contract, httpResourceGroup);
    }
}
