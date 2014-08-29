package com.netflix.client;

import com.netflix.loadbalancer.LoadBalancerExecutor;

public interface ExecutionListener<I, O> {

    /**
     * Called when execution is about to start. 
     */
    public void onExecutionStart(ExecutionContext<I> context);
    
    /**
     * Called when a server is chosen and the request is going to be executed on the server.
     * 
     */
    public void onStartWithServer(ExecutionContext<I> context, ExecutionInfo info);
    
    /**
     * Called when an exception is received from executing the request on a server. 
     * 
     * @param exception Exception received
     */
    public void onExceptionWithServer(ExecutionContext<I> context, Throwable exception,  ExecutionInfo info);
    
    /**
     * Called when the request is executed successfully on the server
     * 
     * @param response Object received from the execution
     */
    public void onExecutionSuccess(ExecutionContext<I> context, O response,  ExecutionInfo info);
    
    /**
     * Called when the request is considered failed after all retries.
     * 
     * @param finalException Final exception received. This may be a wrapped exception indicating that the {@link LoadBalancerExecutor}
     *                           has exhausted all retries.
     */
    public void onExecutionFailed(ExecutionContext<I> context, Throwable finalException, ExecutionInfo info);
}
