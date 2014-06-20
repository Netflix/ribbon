package com.netflix.ribbonclientextensions;

import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
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
    
    String name();
    
    RequestTemplate<I, O, R> copy(String name);
        
    RequestTemplate<I, O, R> withFallbackProvider(FallbackHandler<O> fallbackProvider);
    
    RequestTemplate<I, O, R> withResponseValidator(ResponseValidator<R> transformer);
        
    /**
     * Calling this method will enable both Hystrix request cache and supplied external cache providers  
     * on the supplied cache key. Caller can explicitly disable Hystrix request cache by calling 
     * {@link #withHystrixCommandPropertiesDefaults(com.netflix.hystrix.HystrixCommandProperties.Setter)}
     *     
     * @param cacheKeyTemplate
     * @return
     */
    RequestTemplate<I, O, R> withHystrixCacheKey(String cacheKeyTemplate);

    RequestTemplate<I, O, R> addCacheProvider(String cacheKeyTemplate, CacheProvider<O> cacheProvider);
    
    RequestTemplate<I, O, R> withHystrixProperties(HystrixObservableCommand.Setter setter);
    
    public abstract class RequestBuilder<O> {
        public abstract RequestBuilder<O> withValue(String key, Object value);
        
        public abstract RibbonRequest<O> build();
    }
}
