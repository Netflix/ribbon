package com.netflix.client;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A context object that is created at start of each load balancer execution
 * and contains certain meta data of the load balancer and mutable state data of 
 * execution per listener per request.
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
        this.context = new ConcurrentHashMap<String, Object>();
        this.subContexts = new ConcurrentHashMap<Object, ChildContext<T>>();
        this.retryHandler = retryHandler;
    }

    ExecutionContext(T request, IClientConfig requestConfig, IClientConfig clientConfig, RetryHandler retryHandler, ConcurrentHashMap<Object, ChildContext<T>> subContexts) {
        this.request = request;
        this.requestConfig = requestConfig;
        this.clientConfig = clientConfig;
        this.context = new ConcurrentHashMap<String, Object>();
        this.subContexts = subContexts;
        this.retryHandler = retryHandler;
    }


    ExecutionContext<T> getChildContext(Object obj) {
        if (subContexts == null) {
            return null;
        }
        ChildContext<T> subContext = subContexts.get(obj);
        if (subContext == null) {
            subContext = new ChildContext(this);
        }
        ChildContext<T> old = subContexts.putIfAbsent(obj, subContext);
        if (old != null) {
            return old;
        } else {
            return subContext;
        }
    }

    public T getRequest() {
        return request;
    }

    public Object get(String name) {
        return context.get(name);
    }

    public <S> S getClientProperty(IClientConfigKey<S> key) {
        S value = null;
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

    public IClientConfig getRequestConfig() {
        return requestConfig;
    }

    public ExecutionContext<T> getGlobalContext() {
        return this;
    }

    public RetryHandler getRetryHandler() {
        return retryHandler;
    }
}
