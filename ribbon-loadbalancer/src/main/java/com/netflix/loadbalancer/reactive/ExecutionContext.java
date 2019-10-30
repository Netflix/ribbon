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
import com.netflix.client.config.IClientConfigKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A context object that is created at start of each load balancer execution
 * and contains certain meta data of the load balancer and mutable state data of 
 * execution per listener per request. Each listener will get its own context
 * to work with. But it can also call {@link ExecutionContext#getGlobalContext()} to
 * get the shared context between all listeners.
 * 
 * @author Allen Wang
 *
 */
public class ExecutionContext<T> {

    private final Map<String, Object> context;
    private final ConcurrentHashMap<Object, ChildContext<T>> subContexts;
    private final T request;
    private final IClientConfig requestConfig;
    private final RetryHandler retryHandler;
    private final IClientConfig clientConfig;

    private static class ChildContext<T> extends ExecutionContext<T> {
        private final ExecutionContext<T> parent;

        ChildContext(ExecutionContext<T> parent) {
            super(parent.request, parent.requestConfig, parent.clientConfig, parent.retryHandler, null);
            this.parent = parent;
        }

        @Override
        public ExecutionContext<T> getGlobalContext() {
            return parent;
        }
    }

    public ExecutionContext(T request, IClientConfig requestConfig, IClientConfig clientConfig, RetryHandler retryHandler) {
        this.request = request;
        this.requestConfig = requestConfig;
        this.clientConfig = clientConfig;
        this.context = new ConcurrentHashMap<>();
        this.subContexts = new ConcurrentHashMap<>();
        this.retryHandler = retryHandler;
    }

    ExecutionContext(T request, IClientConfig requestConfig, IClientConfig clientConfig, RetryHandler retryHandler, ConcurrentHashMap<Object, ChildContext<T>> subContexts) {
        this.request = request;
        this.requestConfig = requestConfig;
        this.clientConfig = clientConfig;
        this.context = new ConcurrentHashMap<>();
        this.subContexts = subContexts;
        this.retryHandler = retryHandler;
    }


    ExecutionContext<T> getChildContext(Object obj) {
        if (subContexts == null) {
            return null;
        }
        ChildContext<T> subContext = subContexts.get(obj);
        if (subContext == null) {
            subContext = new ChildContext<T>(this);
            ChildContext<T> old = subContexts.putIfAbsent(obj, subContext);
            if (old != null) {
                subContext = old;
            }
        }
        return subContext;
    }

    public T getRequest() {
        return request;
    }

    public Object get(String name) {
        return context.get(name);
    }

    public <S> S getClientProperty(IClientConfigKey<S> key) {
        S value;
        if (requestConfig != null) {
            value = requestConfig.get(key);
            if (value != null) {
                return value;
            }
        }
        value = clientConfig.get(key);
        return value;
    }
    
    public void put(String name, Object value) {
        context.put(name, value);
    }

    /**
     * @return The IClientConfig object used to override the client's default configuration
     * for this specific execution.
     */
    public IClientConfig getRequestConfig() {
        return requestConfig;
    }

    /**
     *
     * @return The shared context for all listeners.
     */
    public ExecutionContext<T> getGlobalContext() {
        return this;
    }

    public RetryHandler getRetryHandler() {
        return retryHandler;
    }
}
