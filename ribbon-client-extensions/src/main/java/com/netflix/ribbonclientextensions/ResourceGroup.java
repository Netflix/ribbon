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
package com.netflix.ribbonclientextensions;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

public abstract class ResourceGroup<T extends RequestTemplate<?, ?>> {
    private String name;
    private IClientConfig clientConfig;

    public ResourceGroup(String name) {
        this(name, null);
    }

    public ResourceGroup(String name, ClientOptions options) {
        this.name = name;
        clientConfig = loadDefaultConfig(name);
        if (options != null) {
            for (IClientConfigKey key: options.getOptions().keySet()) {
                clientConfig.setPropertyWithType(key, options.getOptions().get(key));
            }
        }
    }
    
    ResourceGroup(IClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }
    
    protected abstract IClientConfig loadDefaultConfig(String name);
    
    protected final IClientConfig getClientConfig() {
        return clientConfig;
    }
    
    public String name() {
        return name;
    }
    
    public abstract <S> T newRequestTemplate(String name, Class<? extends S> classType);
}
