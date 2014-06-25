package com.netflix.ribbonclientextensions;

import java.util.concurrent.Future;

import rx.Observable;

public interface RequestWithMetaData<T> {
    Observable<RibbonResponse<Observable<T>>> observe();
    Observable<RibbonResponse<Observable<T>>> toObservable();
    Future<RibbonResponse<T>> queue();
    RibbonResponse<T> execute();
}
