package com.netflix.client.http;

import java.net.SocketException;
import java.util.List;


import com.google.common.collect.Lists;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.Utils;
import com.netflix.client.config.IClientConfig;

public class HttpRequestRetryHandler extends RequestSpecificRetryHandler{

    public HttpRequestRetryHandler(boolean okToRetryOnConnectErrors,
            boolean okToRetryOnAllErrors, IClientConfig requestConfig,
            RetryHandler delegate) {
        super(okToRetryOnConnectErrors, okToRetryOnAllErrors, requestConfig, delegate);
        // TODO Auto-generated constructor stub
    }
    /*
    @SuppressWarnings("unchecked")
    protected List<Class<? extends Throwable>> connectionRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class);

    private final String httpMethod;
    
    public HttpRequestRetryHandler(boolean okToRetryOnRequest,
            String httpMethod, IClientConfig requestConfig, RetryHandler delegate) {
        super(okToRetryOnRequest, requestConfig, delegate);
        this.httpMethod = httpMethod;
    }

    public boolean isConnectionException(Throwable e) {
        return Utils.isPresentAsCause(e, connectionRelated);
    }

    @Override
    public boolean isRetriableException(Throwable e, boolean sameServer) {
        boolean retryable = super.isRetriableException(e, sameServer);
        if(retryable && httpMethod.equalsIgnoreCase("GET")) {
            return isConnectionException(e);
        }
        return retryable;
    } */
}
