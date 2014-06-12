package com.netflix.ribbonclientextensions;

import java.util.List;

import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

/**
 * @author awang
 *
 * @param <I> request input entity type
 * @param <O> response entity type
 * @param <R> response meta data, e.g. HttpClientResponse
 */
public interface RequestTemplate<I, O, R> {
    
    RequestBuilder<O> requestBuilder();
    
    RequestTemplate<I, O, R> copy();
    
    RequestTemplate<I, O, R> withFallbackProvider(FallbackHandler<O> fallbackProvider);
    
    RequestTemplate<I, O, R> withFallbackDeterminator(FallbackDeterminator<R> fallbackDeterminator);
        
    /**
     * Calling this method will enable both Hystrix request cache and supplied external cache providers  
     * on the supplied cache key. Caller can explicitly disable Hystrix request cache by calling 
     * {@link #withHystrixCommandPropertiesDefaults(com.netflix.hystrix.HystrixCommandProperties.Setter)}
     *     
     * @param cacheProviders External cache providers. Can be empty which means only enable Hystrix internal 
     *              request cache.
     * @param cacheKeyTemplate
     * @return
     */
    RequestTemplate<I, O, R> withCache(String cacheKeyTemplate, List<CacheProvider<O>> cacheProviders);
    
    RequestTemplate<I, O, R> withHystrixCommandPropertiesDefaults(HystrixCommandProperties.Setter setter);
    
    RequestTemplate<I, O, R> withHystrixThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter setter);

    RequestTemplate<I, O, R> withHystrixCollapserPropertiesDefaults(HystrixCollapserProperties.Setter setter);

    public abstract class RequestBuilder<O> {
        public abstract RequestBuilder<O> withValue(String key, Object value);
        
        public abstract RibbonRequest<O> build();
    }
}
