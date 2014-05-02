package com.netflix.ribbonclientextensions.hystrix;

import com.netflix.hystrix.exception.HystrixRuntimeException;

/**
 * Created by mcohen on 4/21/14.
 */
public abstract class FallbackHandler<T> {


    String commandKey;

    private FallbackHandler(){

    }

    public FallbackHandler(String commandKey){
        super();
        this.commandKey = commandKey;
    }

    abstract protected FallbackResponse handleCommandException(HystrixRuntimeException exception);

    abstract protected FallbackResponse handleShortCircuit(HystrixRuntimeException exception);

    abstract protected FallbackResponse handleRejectedSemaphore(HystrixRuntimeException exception);

    abstract protected FallbackResponse handleRejectedSemaphoreFallback(HystrixRuntimeException exception);

    abstract protected  FallbackResponse handleTimeout(HystrixRuntimeException exception);


}
