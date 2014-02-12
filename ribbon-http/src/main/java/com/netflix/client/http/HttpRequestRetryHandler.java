package com.netflix.client.http;

import java.net.SocketException;
import java.util.List;


import com.google.common.collect.Lists;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.Utils;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest.Verb;

public class HttpRequestRetryHandler extends RequestSpecificRetryHandler<HttpRequest>{
    @SuppressWarnings("unchecked")
    protected List<Class<? extends Throwable>> connectionRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class);

    public HttpRequestRetryHandler(HttpRequest request,
            IClientConfig requestConfig, RetryHandler delegate) {
        super(request, requestConfig, delegate);
    }

    public boolean isConnectionException(Throwable e) {
        return Utils.isPresentAsCause(e, connectionRelated);
    }

    @Override
    public boolean isRetriableException(Throwable e, boolean sameServer) {
        boolean retryable = super.isRetriableException(e, sameServer);
        if(retryable && request.getVerb() != Verb.GET) {
            return isConnectionException(e);
        }
        return retryable;
    }
}
