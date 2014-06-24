package com.netflix.ribbonclientextensions;

import java.util.Map;

import rx.Observable;

public interface CacheProvider<T> {
    Observable<T> get(String key, Map<String, Object> requestProperties);
}
