package com.netflix.client;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;

public class RequestSpecificRetryHandler<T extends ClientRequest> implements RetryHandler {

    protected final T request;
    private final RetryHandler fallback;
    private int retrySameServer = -1;
    private int retryNextServer = -1;

    
    public RequestSpecificRetryHandler(T request, @Nullable IClientConfig requestConfig, RetryHandler delegate) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(delegate);
        this.request = request;
        this.fallback = delegate;
        if (requestConfig != null) {
            if (requestConfig.containsProperty(CommonClientConfigKey.MaxAutoRetries)) {
                retrySameServer = requestConfig.getPropertyWithType(CommonClientConfigKey.MaxAutoRetries); 
            }
            if (requestConfig.containsProperty(CommonClientConfigKey.MaxAutoRetriesNextServer)) {
                retryNextServer = requestConfig.getPropertyWithType(CommonClientConfigKey.MaxAutoRetriesNextServer); 
            } 
        }
    }
    
    @Override
    public boolean isRetriableException(Throwable e, boolean sameServer) {
        return request.isRetriable() && fallback.isRetriableException(e, sameServer);
    }

    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        return fallback.isCircuitTrippingException(e);
    }

    @Override
    public boolean isCircuitTrippinResponse(Object response) {
        return fallback.isCircuitTrippinResponse(response);
    }

    @Override
    public int getMaxRetriesOnSameServer() {
        if (retrySameServer >= 0) {
            return retrySameServer;
        }
        return fallback.getMaxRetriesOnSameServer();
    }

    @Override
    public int getMaxRetriesOnNextServer() {
        if (retryNextServer >= 0) {
            return retryNextServer;
        }
        return fallback.getMaxRetriesOnNextServer();
    }    
}
