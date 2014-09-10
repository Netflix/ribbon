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
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import rx.Observable;

import java.net.URI;
import java.util.List;

/**
 * A builder to build {@link com.netflix.loadbalancer.reactive.LoadBalancerCommand}, {@link com.netflix.loadbalancer.reactive.LoadBalancerObservableCommand} and
 * {@link com.netflix.loadbalancer.reactive.LoadBalancerRetrySameServerCommand}.
 *
 * @author Allen Wang
 */
public class CommandBuilder<T> {

    private RetryHandler retryHandler;
    private ILoadBalancer loadBalancer;
    private IClientConfig config;
    private LoadBalancerContext loadBalancerContext;
    private List<? extends ExecutionListener<?, T>> listeners;
    private Object loadBalancerKey;
    private URI serviceLocator;

    private CommandBuilder() {}


    public static <T> CommandBuilder<T> newBuilder() {
        return new CommandBuilder<T>();
    }

    public CommandBuilder<T> withLoadBalancer(ILoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        return this;
    }

    public CommandBuilder<T> withListeners(List<? extends ExecutionListener<?, T>> listeners) {
        this.listeners = listeners;
        return this;
    }

    public CommandBuilder<T> withRetryHandler(RetryHandler retryHandler) {
        this.retryHandler = retryHandler;
        return this;
    }

    public CommandBuilder<T> withClientConfig(IClientConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Pass in an optional URI to help the load balancer to determine which group of servers to choose from.
     * Only the authority of the URI is used.
     */
    public CommandBuilder<T> withServiceLocator(URI serviceLocator) {
        this.serviceLocator = serviceLocator;
        return this;
    }

    /**
     * Pass in an optional key object to help the load balancer to choose a specific server among its
     * server list, depending on the load balancer implementation.
     */
    public CommandBuilder<T> withServerLocator(Object key) {
        this.loadBalancerKey = key;
        return this;
    }

    public CommandBuilder<T> withLoadBalancerContext(LoadBalancerContext loadBalancerContext) {
        this.loadBalancerContext = loadBalancerContext;
        return this;
    }

    public LoadBalancerRetrySameServerCommand<T> build() {
        return build((ExecutionContext<?>) null);
    }

    public LoadBalancerRetrySameServerCommand<T> build(ExecutionContext<?> executionContext) {
        if (loadBalancerContext == null && loadBalancer == null) {
            throw new IllegalArgumentException("Either LoadBalancer or LoadBalancerContext needs to be set");
        }
        ExecutionContextListenerInvoker invoker = null;

        if (listeners != null && listeners.size() > 0 && executionContext != null) {
            invoker = new ExecutionContextListenerInvoker(executionContext, listeners);
        }
        LoadBalancerContext loadBalancerContext1 = loadBalancerContext == null ? new LoadBalancerContext(loadBalancer, config) : loadBalancerContext;
        return new LoadBalancerRetrySameServerCommand<T>(loadBalancerContext1, retryHandler, invoker);
    }

    public LoadBalancerObservableCommand<T> build(final LoadBalancerObservable<T> executable) {
        return build(executable, null);
    }

    public LoadBalancerObservableCommand<T> build(final LoadBalancerObservable<T> executable, ExecutionContext<?> executionContext) {
        if (loadBalancerContext == null && loadBalancer == null) {
            throw new IllegalArgumentException("Either LoadBalancer or LoadBalancerContext needs to be set");
        }
        ExecutionContextListenerInvoker invoker = null;

        if (listeners != null && listeners.size() > 0) {
            invoker = new ExecutionContextListenerInvoker(executionContext, listeners);
        }
        LoadBalancerContext loadBalancerContext1 = loadBalancerContext == null ? new LoadBalancerContext(loadBalancer, config) : loadBalancerContext;
        return new LoadBalancerObservableCommand<T>(loadBalancerContext1, retryHandler, serviceLocator, loadBalancerKey, invoker) {
            @Override
            public Observable<T> run(Server server) {
                return executable.run(server);
            }
        };
    }

    public LoadBalancerCommand<T> build(final LoadBalancerExecutable<T> executable) {
        if (loadBalancerContext == null && loadBalancer == null) {
            throw new IllegalArgumentException("Either LoadBalancer or LoadBalancerContext needs to be set");
        }
        LoadBalancerContext loadBalancerContext1 = loadBalancerContext == null ? new LoadBalancerContext(loadBalancer, config) : loadBalancerContext;
        return new LoadBalancerCommand<T>(loadBalancerContext1, retryHandler, serviceLocator, loadBalancerKey) {
            @Override
            public T run(Server server) throws Exception {
                return executable.run(server);
            }
        };
    }
}
