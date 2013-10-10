package com.netflix.client;

public interface LoadBalancerErrorHandler<T extends ClientRequest, S extends IResponse> {

    public boolean isRetriableException(T request, Throwable e, boolean sameServer);
    
    public boolean isCircuitTrippingException(Throwable e);
    
    public boolean isCircuitTrippinErrorgResponse(S response);
    
}
