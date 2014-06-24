package com.netflix.ribbonclientextensions.hystrix;

import java.util.Map;

import com.netflix.hystrix.HystrixExecutableInfo;

import rx.Observable;

/**
 * 
 * @author awang
 *
 * @param <T> Output entity type
 */
public interface FallbackHandler<T> {
    public Observable<T> getFallback(HystrixExecutableInfo<?> hystrixInfo, Map<String, Object> requestProperties);
}
