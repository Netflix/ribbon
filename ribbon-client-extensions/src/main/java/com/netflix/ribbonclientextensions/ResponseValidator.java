package com.netflix.ribbonclientextensions;

import rx.functions.Action1;

/**
 * 
 * @author awang
 *
 * @param <T> Protocol specific response meta data, e.g., HttpClientResponse
 */
public interface ResponseValidator<T> extends Action1<T> {
    // TODO: define own methods and checked exception
}
