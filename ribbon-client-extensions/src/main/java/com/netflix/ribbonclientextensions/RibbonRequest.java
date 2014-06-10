package com.netflix.ribbonclientextensions;

import java.util.concurrent.Future;

import rx.Observable;

public interface RibbonRequest<T> {

    // public RibbonRequest<Map<String, Object>> asDictionary();
        
    public T execute();
    
    public Future<T> queue();
    
    public Observable<T> observe();
    
    public Observable<T> toObservable();
    
    public RibbonRequest<HystrixResponse<T>> withHystrixInfo();
}
