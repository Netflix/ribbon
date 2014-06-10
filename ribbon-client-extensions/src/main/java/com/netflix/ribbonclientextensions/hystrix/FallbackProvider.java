package com.netflix.ribbonclientextensions.hystrix;

import com.netflix.hystrix.HystrixCommand;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * 
 * @author awang
 *
 * @param <T> Output entity type
 * @param <R> Response 
 */
public interface FallbackProvider<T> extends Func1<HystrixCommand<T>, Observable<T>> {
}
