package com.netflix.ribbonclientextensions;

import java.util.concurrent.Future;

import rx.Observable;


public interface RibbonRequest<T> extends RxRequest<T> {

    @Override
    public T execute();
    
    @Override
    public Future<T> queue();
    
    @Override
    public Observable<T> observe();
    
    @Override
    public Observable<T> toObservable();
    
    public RibbonRequest<RibbonResponse<T>> withMetadata();
}
