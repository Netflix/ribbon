/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon;

import java.util.concurrent.Future;

import rx.Observable;

/**
 * Request that provides blocking and non-blocking APIs to fetch the content.
 *  
 * @author Allen Wang
 *
 */
public interface RibbonRequest<T> {

    /**
     * Blocking API that returns a single (or last element if there is a sequence of objects from the execution) element
     */
    public T execute();
    
    /**
     * Non blocking API that returns a {@link Future}, where its {@link Future#get()} method is blocking and returns a 
     * single (or last element if there is a sequence of objects from the execution) element
     */
    public Future<T> queue();

    /**
     * Non blocking API that returns an {@link Observable} while the execution is started asynchronously.
     * Subscribing to the returned {@link Observable} is guaranteed to get the complete sequence from 
     * the beginning, which might be replayed by the framework. Use this API for "fire and forget".
     */
    public Observable<T> observe();
    
    /**
     * Non blocking API that returns an Observable. The execution is not started until the returned Observable is subscribed to.
     */
    public Observable<T> toObservable();

    /**
     * Create a decorated {@link RequestWithMetaData} where you can call its similar blocking or non blocking 
     * APIs to get {@link RibbonResponse}, which in turn contains returned object(s) and 
     * some meta data from Hystrix execution.
     */
    public RequestWithMetaData<T> withMetadata();
}
