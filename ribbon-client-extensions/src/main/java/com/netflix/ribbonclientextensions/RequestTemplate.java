package com.netflix.ribbonclientextensions;

import java.util.List;

import com.netflix.ribbonclientextensions.hystrix.FallbackProvider;
import com.netflix.ribbonclientextensions.interfaces.CacheProvider;

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
    
    RequestTemplate<I, O, R> withCacheProvider(List<CacheProvider<O>> cacheProviders);
    
    public abstract class RequestBuilder<O> {
        public abstract RequestBuilder<O> withValue(String key, Object value);
        
        public abstract RibbonRequest<O> build();

    }
}
