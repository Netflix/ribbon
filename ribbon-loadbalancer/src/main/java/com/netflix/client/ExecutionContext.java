package com.netflix.client;

import com.google.common.collect.Maps;
import com.netflix.client.config.IClientConfig;

import java.util.Map;

/**
 * A context object that is created at start of each load balancer execution
 * and contains certain meta data of the load balancer and mutable state data of 
 * execution per listener per request.
 * 
 * @author Allen Wang
 *
 */
public class ExecutionContext<T> {

    private final Map<Object, Map<String, Object>> subContexts = Maps.newIdentityHashMap();
    private final Map<String, Object> context;
    private final T request;
    private final IClientConfig config;

    public ExecutionContext(T request, IClientConfig config) {
        this.request = request;
        this.config = config;
        this.context = Maps.newConcurrentMap();
    }

    private ExecutionContext(ExecutionContext<T> parent, Map<String, Object> context) {
        this.request = parent.request;
        this.config = parent.config;
        this.context = context;
    }

    public static <T> ExecutionContext getSubContext(ExecutionContext<T> parent, Object obj) {
        Map<String, Object> subContext = parent.subContexts.get(obj);
        if (subContext == null) {
            subContext = Maps.newConcurrentMap();
            parent.subContexts.put(obj, subContext);
        }
        return new ExecutionContext(parent, subContext);
    }

    public T getRequest() {
        return null;
    }

    public IClientConfig getClientConfig() {return null;}

    public String getClientAppName() {
        return null;
    }
    
    public String getTargetAppName() {
        return null;
    }
    
    public String getVipAddresses() {
        return null;
    }

    public String getClientName() {
        return null;
    }

    public Object get(String name) {
        return null;
    }
    
    public void put(String name, Object value) {        
    }
}
