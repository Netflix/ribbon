package com.netflix.utils;

import rx.Observable;
import rx.util.functions.Func1;

public class RxUtils {

    public static <T> T getSingleValueWithRealErrorCause(Observable<T> observable) throws Exception {
        return observable.onErrorResumeNext(new Func1<Throwable, Observable<T>>(){

            @Override
            public Observable<T> call(Throwable t1) {
                if (t1 instanceof RuntimeException) {
                    return Observable.error(t1.getCause());
                } else {
                    return Observable.error(t1);
                }
            }            
        }).toBlockingObservable().single();
    }
}
