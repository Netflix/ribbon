package com.netflix.client.netty.http;

import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;

import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.LoadBalancerExecutor;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;

public class AbstractLoadBalancingClient {

    protected LoadBalancerExecutor lbExecutor;
    
    protected final void setLoadBalancerExecutor(LoadBalancerExecutor lbExecutor) {
        this.lbExecutor = lbExecutor;
    }
    
    protected RequestSpecificRetryHandler getRequestRetryHandler(HttpClientRequest<?> request, IClientConfig requestConfig) {
        boolean okToRetryOnAllErrors = request.getMethod().equals(HttpMethod.GET);
        return new RequestSpecificRetryHandler(true, okToRetryOnAllErrors, lbExecutor.getErrorHandler(), requestConfig);
    }
        
    public <I> RepeatableContentHttpRequest<I> getRepeatableRequest(HttpClientRequest<I> original) {
        if (original instanceof RepeatableContentHttpRequest) {
            return (RepeatableContentHttpRequest<I>) original;
        }
        return new RepeatableContentHttpRequest<I>(original);
    }

    public int getMaxAutoRetriesNextServer() {
        if (lbExecutor.getErrorHandler() != null) {
            return lbExecutor.getErrorHandler().getMaxRetriesOnNextServer();
        }
        return 0;
    }

    public int getMaxAutoRetries() {
        if (lbExecutor.getErrorHandler() != null) {
            return lbExecutor.getErrorHandler().getMaxRetriesOnSameServer();
        }
        return 0;
    }

    public ServerStats getServerStats(Server server) {
        return lbExecutor.getServerStats(server);
    }
    
    protected final void setDefaultRetryHandler(RetryHandler errorHandler) {
        lbExecutor.setErrorHandler(errorHandler);
    }
}
