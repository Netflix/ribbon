package com.netflix.client;

import java.util.List;

/**
 * @author Allen Wang
 */
public class ExecutionContextListenerInvoker<I, O> {
    private final ExecutionContext<I> context;
    private final List<ExecutionListener<I, O>> listeners;

    public ExecutionContextListenerInvoker(ExecutionContext<I> context, List<ExecutionListener<I, O>> listeners) {
        this.listeners = listeners;
        this.context = context;
    }

    public void onExecutionStart() {
        for (ExecutionListener<I, O> listener: listeners) {
            try {
                listener.onExecutionStart(context);
            } catch (Throwable e) {
            }
        }
    }

    /**
     * Called when a server is chosen and the request is going to be executed on the server.
     *
     */
    public void onStartWithServer(ExecutionInfo info) {
        for (ExecutionListener<I, O> listener: listeners) {
            try {
                listener.onStartWithServer(context, info);
            } catch (Throwable e) {
            }
        }
    }

    /**
     * Called when an exception is received from executing the request on a server.
     *
     * @param exception Exception received
     */
    public void onExceptionWithServer(Throwable exception, ExecutionInfo info) {
        for (ExecutionListener<I, O> listener: listeners) {
            try {
                listener.onExceptionWithServer(context, exception, info);
            } catch (Throwable e) {
            }
        }
    }

    /**
     * Called when the request is executed successfully on the server
     *
     * @param response Object received from the execution
     */
    public void onExecutionSuccess(O response, ExecutionInfo info) {
        for (ExecutionListener<I, O> listener: listeners) {
            try {
                listener.onExecutionSuccess(context, response, info);
            } catch (Throwable e) {
            }
        }
    }

    /**
     * Called when the request is considered failed after all retries.
     *
     * @param finalException Final exception received. This may be a wrapped exception indicating that the {@link com.netflix.loadbalancer.LoadBalancerExecutor}
     *                           has exhausted all retries.
     */
    public void onExecutionFailed(Throwable finalException, ExecutionInfo info) {
        for (ExecutionListener<I, O> listener: listeners) {
            try {
                listener.onExecutionFailed(context, finalException, info);
            } catch (Throwable e) {
            }
        }
    }

}
