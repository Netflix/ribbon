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

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

/**
 * @author awang
 *
 * @param <T> response entity type
 * @param <R> response meta data, e.g. HttpClientResponse
 */
public abstract class RequestTemplate<T, R> {
    
    public abstract RequestBuilder<T> requestBuilder();
    
    public abstract String name();
    
    public abstract RequestTemplate<T, R> copy(String name);
        
    public abstract RequestTemplate<T, R> withFallbackProvider(FallbackHandler<T> fallbackProvider);
    
    public abstract RequestTemplate<T, R> withResponseValidator(ResponseValidator<R> transformer);
        
    /**
     * Calling this method will enable both Hystrix request cache and supplied external cache providers  
     * on the supplied cache key. Caller can explicitly disable Hystrix request cache by calling 
     * {@link #withHystrixProperties(com.netflix.hystrix.HystrixObservableCommand.Setter)}
     *     
     * @param cacheKeyTemplate
     * @return
     */
    public abstract RequestTemplate<T, R> withRequestCacheKey(String cacheKeyTemplate);

    public abstract RequestTemplate<T, R> addCacheProvider(String cacheKeyTemplate, CacheProvider<T> cacheProvider);
    
    public abstract RequestTemplate<T, R> withHystrixProperties(HystrixObservableCommand.Setter setter);
    
    public static abstract class RequestBuilder<T> {
        public abstract RequestBuilder<T> withRequestProperty(String key, Object value);
        
        public abstract RibbonRequest<T> build();
    }
}
