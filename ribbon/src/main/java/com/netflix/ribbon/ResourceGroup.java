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
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

public abstract class ResourceGroup<T extends RequestTemplate<?, ?>> {
    private String name;
    private IClientConfig clientConfig;

    public static abstract class TemplateBuilder<T extends RequestTemplate> {
        public abstract T build();
    }

    protected ResourceGroup(String name) {
        this(name, ClientOptions.create(), ClientConfigFactory.DEFAULT, RibbonTransportFactory.DEFAULT);
    }

    protected ResourceGroup(String name, ClientOptions options, ClientConfigFactory configFactory, RibbonTransportFactory transportFactory) {
        this.name = name;
        clientConfig = configFactory.newConfig();
        clientConfig.loadProperties(name);
        if (options != null) {
            for (IClientConfigKey key: options.getOptions().keySet()) {
                clientConfig.set(key, options.getOptions().get(key));
            }
        }
    }

    protected final IClientConfig getClientConfig() {
        return clientConfig;
    }

    public final String name() {
        return name;
    }
    
    public abstract <S> TemplateBuilder newTemplateBuilder(String name, Class<? extends S> classType);
}
