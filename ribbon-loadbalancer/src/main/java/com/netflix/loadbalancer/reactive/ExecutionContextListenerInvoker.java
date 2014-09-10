/*
 *
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.loadbalancer.reactive;

import com.netflix.loadbalancer.reactive.ExecutionListener.AbortExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class to invoke the list of {@link ExecutionListener} with {@link ExecutionContext}
 *
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
                    listener.onExecutionStart(context.getChildContext(listener));
                } catch (Throwable e) {
                    if (e instanceof AbortExecutionException) {
                        throw (AbortExecutionException) e;
                    }
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
                listener.onStartWithServer(context.getChildContext(listener), info);
            } catch (Throwable e) {
                if (e instanceof AbortExecutionException) {
                    throw (AbortExecutionException) e;
                }
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
                listener.onExceptionWithServer(context.getChildContext(listener), exception, info);
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
                listener.onExecutionSuccess(context.getChildContext(listener), response, info);
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
                listener.onExecutionFailed(context.getChildContext(listener), finalException, info);
            } catch (Throwable e) {
                logger.error("Error invoking listener " + listener, e);
            }
        }
    }
}
