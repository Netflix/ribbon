package com.netflix.ribbonclientextensions;

import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.ribbonclientextensions.hystrix.FallbackProvider;

/**
 * @author awang
 *
 * @param <I> request input entity type
 * @param <O> response entity type
 * @param <R> response meta data, e.g. HttpClientResponse
 */
public interface RequestTemplate<I, O, R> {
    
    RequestBuilder<O> requestBuilder();
    
    RequestTemplate<I, O, R> withFallbackProvider(FallbackProvider<O> fallbackProvider);
    
    RequestTemplate<I, O, R> withFallbackDeterminator(FallbackDeterminator<R> fallbackDeterminator);
    
    RequestTemplate<I, O, R> addCacheProvider(CacheProvider<O> cacheProvider, String cacheKeyTemplate);
    
    RequestTemplate<I, O, R> withHystrixCommandPropertiesDefaults(HystrixCommandProperties.Setter setter);
    
    RequestTemplate<I, O, R> withHystrixThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter setter);

    RequestTemplate<I, O, R> withHystrixCollapserPropertiesDefaults(HystrixCollapserProperties.Setter setter);

    public abstract class RequestBuilder<O> {
        public abstract RequestBuilder<O> withValue(String key, Object value);
        
        public abstract RibbonRequest<O> build();
    }
}
