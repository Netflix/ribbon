package com.netflix.ribbonclientextensions.hystrix;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.hystrix.HystrixObservableCommand;

import rx.Observable;
import rx.functions.Func1;

/**
 * 
 * @author awang
 *
 * @param <T> Output entity type
 * @param <R> Response 
 */
public interface FallbackHandler<T> extends Func1<HystrixExecutableInfo<?>, Observable<T>> {
}
