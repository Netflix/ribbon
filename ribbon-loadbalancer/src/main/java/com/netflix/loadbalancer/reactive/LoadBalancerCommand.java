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

import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import rx.Observable;

import java.net.URI;

/**
 * A load balancer command that executes in blocking mode.
 *
 * @author Allen Wang
 */
public abstract class LoadBalancerCommand<T> implements LoadBalancerExecutable<T> {

    private final LoadBalancerObservableCommand<T> observableCommand;
    private final URI loadBalancerURI;
    private final Object loadBalancerKey;
    private final LoadBalancerContext loadBalancerContext;
    private final RetryHandler retryHandler;


    public LoadBalancerCommand(LoadBalancerContext loadBalancerContext) {
        this(loadBalancerContext, null, null, null);
    }

    public LoadBalancerCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler) {
        this(loadBalancerContext, retryHandler, null, null);
    }

    public LoadBalancerCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler, URI loadBalancerURI, Object loadBalancerKey) {
        this.loadBalancerContext = loadBalancerContext;
        this.retryHandler = retryHandler;
        this.loadBalancerURI = loadBalancerURI;
        this.loadBalancerKey = loadBalancerKey;
        this.observableCommand = createObservableCommand();
    }

    private LoadBalancerObservableCommand<T> createObservableCommand() {
        return new LoadBalancerObservableCommand<T>(loadBalancerContext, retryHandler, loadBalancerURI, loadBalancerKey, null) {
            @Override
            public Observable<T> call(final Server server) {
                return CommandToObservableConverter.toObsevable(LoadBalancerCommand.this).call(server);
            }
        };
    }

    public T execute() throws Exception {
        return RxUtils.getSingleValueWithRealErrorCause(observableCommand.toObservable());
    }
}
