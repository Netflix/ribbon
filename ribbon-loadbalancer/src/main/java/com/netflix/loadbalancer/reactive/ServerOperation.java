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

import com.netflix.loadbalancer.Server;

/**
 * Provide the {@link rx.Observable} for a specified server. Used by {@link com.netflix.loadbalancer.reactive.LoadBalancerCommand}
 *
 * @param <T> Output type
 */
public interface ServerOperation<T> extends Func1<Server, Observable<T>> {
    /**
     * @return A lazy {@link Observable} for the server supplied. It is expected
     * that the actual execution is not started until the returned {@link Observable} is subscribed to.
     */
    @Override
    public Observable<T> call(Server server);
}
