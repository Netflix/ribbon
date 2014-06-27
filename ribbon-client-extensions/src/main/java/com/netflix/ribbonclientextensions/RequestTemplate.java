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
    
    private final String name;
    private final ResourceGroup<?> group;
    private final Class<? extends T> classType;
    private FallbackHandler<T> fallbackHandler;

    public RequestTemplate(String name,
            ResourceGroup<?> group,
            Class<? extends T> classType) {
        super();
        this.name = name;
        this.group = group;
        this.classType = classType;
    }
    
    public final String name() {
        return name;
    }

    public final ResourceGroup<?> group() {
        return group;
    }

    public final Class<? extends T> classType() {
        return classType;
    }


    public abstract RequestBuilder<T> requestBuilder();
    
    public abstract RequestTemplate<T, R> copy(String name);
        
    public RequestTemplate<T, R> withFallbackProvider(FallbackHandler<T> fallbackHandler) {
        this.fallbackHandler = fallbackHandler;
        return this;
    }
    
    public FallbackHandler<T> fallbackHandler() {
        return fallbackHandler;
    }

    public abstract RequestTemplate<T, R> withResponseValidator(ResponseValidator<R> validator);
        
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
