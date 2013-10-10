package com.netflix.client;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;

import com.google.common.collect.Lists;

public class DefaultLoadBalancerErrorHandler<T extends ClientRequest, S extends IResponse> 
        implements LoadBalancerErrorHandler<T, S> {

    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> retriable = 
            Lists.<Class<? extends Throwable>>newArrayList(ConnectException.class, SocketTimeoutException.class);
    
    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> circuitRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class, SocketTimeoutException.class);

    
    @Override
    public boolean isRetriableException(T request, Throwable e,
            boolean sameServer) {
        if (!request.isRetriable()) {
            return false;
        } else {
            return LoadBalancerContext.isPresentAsCause(e, retriable);
        }
    }

    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        return LoadBalancerContext.isPresentAsCause(e, circuitRelated);        
    }

    @Override
    public boolean isCircuitTrippinErrorgResponse(S response) {
        return false;
    }
}
