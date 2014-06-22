package com.netflix.ribbonclientextensions;

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

/**
 * @author awang
 *
 * @param <I> request input entity type
 * @param <O> response entity type
 * @param <R> response meta data, e.g. HttpClientResponse
 */
public interface RequestTemplate<T, R> {
    
    RequestBuilder<T> requestBuilder();
    
    String name();
    
    RequestTemplate<T, R> copy(String name);
        
    RequestTemplate<T, R> withFallbackProvider(FallbackHandler<T> fallbackProvider);
    
    RequestTemplate<T, R> withResponseValidator(ResponseValidator<R> transformer);
        
    /**
     * Calling this method will enable both Hystrix request cache and supplied external cache providers  
     * on the supplied cache key. Caller can explicitly disable Hystrix request cache by calling 
     * {@link #withHystrixCommandPropertiesDefaults(com.netflix.hystrix.HystrixCommandProperties.Setter)}
     *     
     * @param cacheKeyTemplate
     * @return
     */
    RequestTemplate<T, R> withRequestCacheKey(String cacheKeyTemplate);

    RequestTemplate<T, R> addCacheProvider(String cacheKeyTemplate, CacheProvider<T> cacheProvider);
    
    RequestTemplate<T, R> withHystrixProperties(HystrixObservableCommand.Setter setter);
    
    public abstract class RequestBuilder<T> {
        public abstract RequestBuilder<T> withRequestProperty(String key, Object value);
        
        public abstract RibbonRequest<T> build();
    }
}
