package com.netflix.ribbon.http.hystrix;

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbon.CacheProvider;
import rx.Observable;

import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class HystrixCacheObservableCommand<T> extends HystrixObservableCommand<T> {

    private final CacheProvider<T> cacheProvider;
    private final String key;
    private final Map<String, Object> requestProperties;

    public HystrixCacheObservableCommand(
            CacheProvider<T> cacheProvider,
            String key,
            Map<String, Object> requestProperties,
            Setter setter) {
        super(setter);
        this.cacheProvider = cacheProvider;
        this.key = key;
        this.requestProperties = requestProperties;
    }

    @Override
    protected Observable<T> run() {
        return cacheProvider.get(key, requestProperties);
    }
}
