package com.netflix.client;

import com.netflix.client.config.IClientConfig;

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
    private final IClientConfig config;
    private final RetryHandler retryHandler;

    ExecutionContext() {
        context = new ConcurrentHashMap<String, Object>();
        request = null;
        config = null;
        subContexts = new ConcurrentHashMap<Object, ChildContext<T>>();
        retryHandler = null;
    }

    private class ChildContext<T> extends ExecutionContext<T> {
        private final ExecutionContext<T> parent;

        ChildContext(ExecutionContext<T> parent) {
            super();
            this.parent = parent;
        }

        @Override
        public T getRequest() {
            return parent.getRequest();
        }

        @Override
        public IClientConfig getOverrideConfig() {
            return parent.getOverrideConfig();
        }

        @Override
        public RetryHandler getRetryHandler() {
            return parent.getRetryHandler();
        }

        @Override
        public Object get(String name) {
            Object fromChild = context.get(name);
            if (fromChild != null) {
                return fromChild;
            } else {
                return parent.get(name);
            }
        }
    }

    public ExecutionContext(T request, IClientConfig config, RetryHandler retryHandler) {
        this.request = request;
        this.config = config;
        this.context = new ConcurrentHashMap<String, Object>();
        this.subContexts = new ConcurrentHashMap<Object, ChildContext<T>>();
        this.retryHandler = retryHandler;
    }

    public ExecutionContext<T> getChildContext(Object obj) {
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

    public IClientConfig getOverrideConfig() {
        return config;
    }

    public Object get(String name) {
        return context.get(name);
    }
    
    public void put(String name, Object value) {
        context.put(name, value);
    }

    public RetryHandler getRetryHandler() {
        return retryHandler;
    }
}
