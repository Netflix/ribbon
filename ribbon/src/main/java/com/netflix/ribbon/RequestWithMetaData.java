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
 * A decorated request object whose response content contains the execution meta data.
 * 
 * @author Allen Wang
 *
 */
public interface RequestWithMetaData<T> {
    /**
     * Non blocking API that returns an {@link Observable} while the execution is started asynchronously.
     * Subscribing to the returned {@link Observable} is guaranteed to get the complete sequence from 
     * the beginning, which might be replayed by the framework. 
     */    
    Observable<RibbonResponse<Observable<T>>> observe();
    
    /**
     * Non blocking API that returns an Observable. The execution is not started until the returned Observable is subscribed to.
     */
    Observable<RibbonResponse<Observable<T>>> toObservable();
    
    /**
     * Non blocking API that returns a {@link Future}, where its {@link Future#get()} method is blocking and returns a 
     * single (or last element if there is a sequence of objects from the execution) element
     */
    Future<RibbonResponse<T>> queue();
    
    /**
     * Blocking API that returns a single (or last element if there is a sequence of objects from the execution) element
     */
    RibbonResponse<T> execute();
}
