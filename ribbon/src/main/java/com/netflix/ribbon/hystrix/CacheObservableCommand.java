package com.netflix.ribbon.hystrix;

import java.util.Map;

import rx.Observable;

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbon.CacheProvider;

/**
 * @author Tomasz Bak
 */
public class CacheObservableCommand<T> extends HystrixObservableCommand<T> {

    private final CacheProvider<T> cacheProvider;
    private final String key;
    private final String hystrixCacheKey;
    private final Map<String, Object> requestProperties;

    public CacheObservableCommand(
            CacheProvider<T> cacheProvider,
            String key,
            String hystrixCacheKey,
            Map<String, Object> requestProperties,
            Setter setter) {
        super(setter);
        this.cacheProvider = cacheProvider;
        this.key = key;
        this.hystrixCacheKey = hystrixCacheKey;
        this.requestProperties = requestProperties;
    }

    @Override
    protected String getCacheKey() {
        if (hystrixCacheKey == null) {
            return super.getCacheKey();
        } else {
            return hystrixCacheKey;
        }
    }

    @Override
    protected Observable<T> construct() {
        return cacheProvider.get(key, requestProperties);
    }
}
