package com.netflix.client.netty.http;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;


import com.google.common.collect.Lists;
import com.netflix.client.LoadBalancerContext;
import com.netflix.client.LoadBalancerErrorHandler;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

public class NettyHttpLoadBalancerErrorHandler implements LoadBalancerErrorHandler<HttpRequest, HttpResponse> {

    @SuppressWarnings("unchecked")
    protected List<Class<? extends Throwable>> retriable = 
            Lists.<Class<? extends Throwable>>newArrayList(ConnectException.class, SocketTimeoutException.class, 
                    io.netty.handler.timeout.ReadTimeoutException.class, io.netty.channel.ConnectTimeoutException.class);
    
    @SuppressWarnings("unchecked")
    protected List<Class<? extends Throwable>> circuitRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class, SocketTimeoutException.class, 
                    io.netty.handler.timeout.ReadTimeoutException.class, io.netty.channel.ConnectTimeoutException.class);
    
    /**
     * @return true if request is retriable and the Throwable has any of the following type of exception as a cause: 
     * {@link ConnectException}, {@link SocketTimeoutException}, {@link NoHttpResponseException}, {@link ConnectionPoolTimeoutException}
     * 
     */
    @Override
    public boolean isRetriableException(HttpRequest request, Throwable e,
            boolean sameServer) {
        if (request.isRetriable() && LoadBalancerContext.isPresentAsCause(e, retriable)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return true if the Throwable has one of the following exception type as a cause: 
     * {@link SocketException}, {@link SocketTimeoutException}
     */
    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        return LoadBalancerContext.isPresentAsCause(e, circuitRelated);
    }

    /**
     * @return true if the the response has status code 503 (throttle) 
     */
    @Override
    public boolean isCircuitTrippinErrorgResponse(HttpResponse response) {
        return response.getStatus() == 503;
    }
}
