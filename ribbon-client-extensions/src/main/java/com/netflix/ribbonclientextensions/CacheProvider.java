package com.netflix.ribbonclientextensions;

import rx.Observable;

public interface CacheProvider<T> {
    Observable<T> get(String key);
}
