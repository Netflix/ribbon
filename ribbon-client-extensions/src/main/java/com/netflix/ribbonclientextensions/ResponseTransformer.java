package com.netflix.ribbonclientextensions;

import rx.functions.Func1;

/**
 * 
 * @author awang
 *
 * @param <T> Protocol specific response meta data, e.g., HttpClientResponse
 */
public interface ResponseTransformer<T> extends Func1<T, T> {
}
