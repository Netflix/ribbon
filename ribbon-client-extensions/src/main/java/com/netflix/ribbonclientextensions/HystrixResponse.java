package com.netflix.ribbonclientextensions;

import java.util.concurrent.Future;

import rx.Observable;

import com.netflix.hystrix.HystrixExecutableInfo;

public interface HystrixResponse<T> {
    HystrixExecutableInfo<T> getHystrixInfo();   
    
    T execute();
    
    Observable<T> toObservable();
    
    Observable<T> observe();
    
    Future<T> queue();
}
