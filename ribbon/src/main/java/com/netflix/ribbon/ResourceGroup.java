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
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbon.hystrix.FallbackHandler;

public abstract class ResourceGroup<T extends RequestTemplate<?, ?>> {
    protected final String name;
    protected final IClientConfig clientConfig;
    protected final ClientConfigFactory configFactory;
    protected final RibbonTransportFactory transportFactory;

    public static abstract class GroupBuilder<T extends ResourceGroup> {
        public abstract T build();

        public abstract GroupBuilder withClientOptions(ClientOptions options);
    }

    public static abstract class TemplateBuilder<S, R, T extends RequestTemplate<S, R>> {
        public abstract TemplateBuilder withFallbackProvider(FallbackHandler<S> fallbackProvider);

        public abstract TemplateBuilder withResponseValidator(ResponseValidator<R> transformer);

        /**
         * Calling this method will enable both Hystrix request cache and supplied external cache providers
         * on the supplied cache key. Caller can explicitly disable Hystrix request cache by calling
         * {@link #withHystrixProperties(com.netflix.hystrix.HystrixObservableCommand.Setter)}
         *
         * @param cacheKeyTemplate
         * @return
         */
        public abstract TemplateBuilder withRequestCacheKey(String cacheKeyTemplate);

        public abstract TemplateBuilder withCacheProvider(String cacheKeyTemplate, CacheProvider<S> cacheProvider);

        public abstract TemplateBuilder withHystrixProperties(HystrixObservableCommand.Setter setter);

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
        this.configFactory = configFactory;
        this.transportFactory = transportFactory;
    }

    protected final IClientConfig getClientConfig() {
        return clientConfig;
    }

    public final String name() {
        return name;
    }
    
    public abstract <S> TemplateBuilder<S, ?, ?> newTemplateBuilder(String name, Class<? extends S> classType);
}
