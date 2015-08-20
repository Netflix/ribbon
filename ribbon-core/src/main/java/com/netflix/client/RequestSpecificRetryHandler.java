package com.netflix.client;

import java.net.SocketException;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;

/**
 * Implementation of RetryHandler created for each request which allows for request
 * specific override
 * @author elandau
 *
 */
public class RequestSpecificRetryHandler implements RetryHandler {

    private final RetryHandler fallback;
    private int retrySameServer = -1;
    private int retryNextServer = -1;
    private final boolean okToRetryOnConnectErrors;
    private final boolean okToRetryOnAllErrors;
    
    protected List<Class<? extends Throwable>> connectionRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class);

    public RequestSpecificRetryHandler(boolean okToRetryOnConnectErrors, boolean okToRetryOnAllErrors) {
        this(okToRetryOnConnectErrors, okToRetryOnAllErrors, RetryHandler.DEFAULT, null);    
    }
    
    public RequestSpecificRetryHandler(boolean okToRetryOnConnectErrors, boolean okToRetryOnAllErrors, RetryHandler baseRetryHandler, @Nullable IClientConfig requestConfig) {
        Preconditions.checkNotNull(baseRetryHandler);
        this.okToRetryOnConnectErrors = okToRetryOnConnectErrors;
        this.okToRetryOnAllErrors = okToRetryOnAllErrors;
        this.fallback = baseRetryHandler;
        if (requestConfig != null) {
            if (requestConfig.containsProperty(CommonClientConfigKey.MaxAutoRetries)) {
                retrySameServer = requestConfig.get(CommonClientConfigKey.MaxAutoRetries); 
            }
            if (requestConfig.containsProperty(CommonClientConfigKey.MaxAutoRetriesNextServer)) {
                retryNextServer = requestConfig.get(CommonClientConfigKey.MaxAutoRetriesNextServer); 
            } 
        }
    }
    
    public boolean isConnectionException(Throwable e) {
        return Utils.isPresentAsCause(e, connectionRelated);
    }

    @Override
    public boolean isRetriableException(Throwable e, boolean sameServer) {
        if (okToRetryOnAllErrors) {
            return true;
        } 
        else if (e instanceof ClientException) {
            ClientException ce = (ClientException) e;
            if (ce.getErrorType() == ClientException.ErrorType.SERVER_THROTTLED) {
                return !sameServer;
            } else {
                return false;
            }
        } 
        else  {
            return okToRetryOnConnectErrors && isConnectionException(e);
        }
    }

    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        return fallback.isCircuitTrippingException(e);
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
