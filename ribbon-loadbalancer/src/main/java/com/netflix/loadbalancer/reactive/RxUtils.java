/*
 *
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.loadbalancer.reactive;

import rx.Observable;
import rx.functions.Func1;

class RxUtils {

    public static <T> T getSingleValueWithRealErrorCause(Observable<T> observable) throws Exception {
        return observable.onErrorResumeNext(new Func1<Throwable, Observable<T>>(){

            @Override
            public Observable<T> call(Throwable t1) {
                if ((t1 instanceof RuntimeException) && t1.getCause() != null) {
                    return Observable.error(t1.getCause());
                } else {
                    return Observable.error(t1);
                }
            }            
        }).toBlocking().single();
    }
}
