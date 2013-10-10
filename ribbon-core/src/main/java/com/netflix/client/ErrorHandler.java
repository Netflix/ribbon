package com.netflix.client;

public interface ErrorHandler<T extends ClientRequest, S extends IResponse> {

    public boolean isRetriableException(T request, Throwable e);
    
    public boolean isRetriableErrorResponse(T request, S response);
    
    public boolean isCircuitTrippingException(Throwable e);
    
    public boolean isCircuitTrippinErrorgResponse(S response);
}
