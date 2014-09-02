package com.netflix.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Allen Wang
 */
public class ExecutionContextListenerInvoker<I, O> {

    private final static Logger logger = LoggerFactory.getLogger(ExecutionContextListenerInvoker.class);
    private final ExecutionContext<I> context;
    private final List<ExecutionListener<I, O>> listeners;
    private final AtomicBoolean onStartInvoked = new AtomicBoolean();

    public ExecutionContextListenerInvoker(ExecutionContext<I> context, List<ExecutionListener<I, O>> listeners) {
        this.listeners = listeners;
        this.context = context;
    }

    public void onExecutionStart() {
        if (onStartInvoked.compareAndSet(false, true)) {
            for (ExecutionListener<I, O> listener : listeners) {
                try {
                    listener.onExecutionStart(context);
                } catch (Throwable e) {
                    logger.error("Error invoking listener " + listener, e);
                }
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
                logger.error("Error invoking listener " + listener, e);
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
                logger.error("Error invoking listener " + listener, e);
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
                logger.error("Error invoking listener " + listener, e);
            }
        }
    }

    /**
     * Called when the request is considered failed after all retries.
     *
     * @param finalException Final exception received.
     */
    public void onExecutionFailed(Throwable finalException, ExecutionInfo info) {
        for (ExecutionListener<I, O> listener: listeners) {
            try {
                listener.onExecutionFailed(context, finalException, info);
            } catch (Throwable e) {
                logger.error("Error invoking listener " + listener, e);
            }
        }
    }
}
