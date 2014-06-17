package com.netflix.ribbonclientextensions;

import java.util.concurrent.Future;

import rx.Observable;

import com.netflix.hystrix.HystrixExecutableInfo;

public interface RibbonResponse<T> extends RxRequest<T> {
    @Override
    public T execute();
    
    @Override
    public Future<T> queue();
    
    @Override
    public Observable<T> observe();
    
    @Override
    public Observable<T> toObservable();
    
    HystrixExecutableInfo<T> getHystrixInfo();   
}
