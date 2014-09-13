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

import com.netflix.client.ClientException;
import com.netflix.loadbalancer.reactive.ExecutionListener.AbortExecutionException;
import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.servo.monitor.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.observers.SafeSubscriber;
import rx.subscriptions.SerialSubscription;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.netflix.loadbalancer.reactive.CommandToObservableConverter.toObsevable;

/**
 * A command that can be used to execute retry on a same server.
 *
 * @author Allen Wang
 */
public class LoadBalancerRetrySameServerCommand<T> {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerObservableCommand.class);

    protected final LoadBalancerContext loadBalancerContext;
    private final RetryHandler retryHandler;
    protected final ExecutionContextListenerInvoker<?, T> listenerInvoker;
    protected volatile ExecutionInfo executionInfo;


    public LoadBalancerRetrySameServerCommand(LoadBalancerContext loadBalancerContext) {
        this(loadBalancerContext, null);
    }

    public LoadBalancerRetrySameServerCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler) {
        this(loadBalancerContext, retryHandler, null);
    }

    public LoadBalancerRetrySameServerCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler, ExecutionContextListenerInvoker<?, T> listenerInvoker) {
        this.loadBalancerContext = loadBalancerContext;
        this.retryHandler = retryHandler;
        this.listenerInvoker = listenerInvoker;
    }

    protected final RetryHandler getRetryHandler() {
        return retryHandler != null ? retryHandler : loadBalancerContext.getRetryHandler();
    }

    private class RetrySameServerOperator implements Operator<T, T> {
        private final Server server;
        private final Observable<T> singleHostObservable;
        private final RetryHandler errorHandler = getRetryHandler();
        private final AtomicInteger counter = new AtomicInteger();
        private final int numberServersAttempted;
        private final boolean invokeOnStartAndEnd;

        RetrySameServerOperator(final Server server, final Observable<T> singleHostObservable, int numberServersAttempted) {
            this.server = server;
            this.singleHostObservable = singleHostObservable;
            this.numberServersAttempted = numberServersAttempted;
            invokeOnStartAndEnd = false;
        }

        RetrySameServerOperator(final Server server, final Observable<T> singleHostObservable) {
            this.server = server;
            this.singleHostObservable = singleHostObservable;
            this.numberServersAttempted = 0;
            invokeOnStartAndEnd = true;
        }

        @Override
        public Subscriber<? super T> call(final Subscriber<? super T> t1) {
            if (listenerInvoker != null) {
                executionInfo = ExecutionInfo.create(server, counter.get(), numberServersAttempted);
                try {
                    if (invokeOnStartAndEnd && counter.get() == 0) {
                        listenerInvoker.onExecutionStart();
                    }
                    listenerInvoker.onStartWithServer(executionInfo);
                } catch (AbortExecutionException e) {
                    throw e;
                }
            }
            SerialSubscription serialSubscription = new SerialSubscription();
            t1.add(serialSubscription);
            final ServerStats serverStats = loadBalancerContext.getServerStats(server);
            loadBalancerContext.noteOpenConnection(serverStats);
            final Stopwatch tracer = loadBalancerContext.getExecuteTracer().start();
            Subscriber<T> subscriber = new Subscriber<T>() {
                private volatile T entity;

                @Override
                public void onCompleted() {
                    recordStats(entity, null);
                    if (listenerInvoker != null) {
                        executionInfo = ExecutionInfo.create(server, counter.get(), numberServersAttempted);
                        listenerInvoker.onExecutionSuccess(entity, executionInfo);
                    }
                    t1.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    logger.debug("Got error {} when executed on server {}", e, server);
                    if (listenerInvoker != null) {
                        executionInfo = ExecutionInfo.create(server, counter.get(), numberServersAttempted);
                        listenerInvoker.onExceptionWithServer(e, executionInfo);
                    }
                    recordStats(entity, e);
                    int maxRetries = errorHandler.getMaxRetriesOnSameServer();
                    boolean shouldRetry = maxRetries > 0 && errorHandler.isRetriableException(e, true);
                    final Throwable finalThrowable;
                    if (shouldRetry && !loadBalancerContext.handleSameServerRetry(server, counter.incrementAndGet(), maxRetries, e)) {
                        executionInfo = ExecutionInfo.create(server, counter.get(), numberServersAttempted);
                        finalThrowable = new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
                                "Number of retries exceeded max " + maxRetries + " retries, while making a call for: " + server, e);
                        shouldRetry = false;
                    } else {
                        finalThrowable = e;
                    }

                    if (shouldRetry) {
                        singleHostObservable.lift(RetrySameServerOperator.this).unsafeSubscribe(t1);
                    } else {
                        if (listenerInvoker != null && invokeOnStartAndEnd) {
                            listenerInvoker.onExecutionFailed(finalThrowable, executionInfo);
                        }
                        t1.onError(finalThrowable);
                    }
                }

                @Override
                public void onNext(T obj) {
                    entity = obj;
                    t1.onNext(obj);
                }

                private void recordStats(Object entity, Throwable exception) {
                    tracer.stop();
                    long duration = tracer.getDuration(TimeUnit.MILLISECONDS);
                    loadBalancerContext.noteRequestCompletion(serverStats, entity, exception, duration, errorHandler);
                }
            };
            Subscriber<T> safeSubscriber = new SafeSubscriber<T>(subscriber);
            serialSubscription.set(safeSubscriber);
            return safeSubscriber;
        }
    }

    /**
     * Gets the {@link Observable} that represents the result of executing on a server, after possible retries as dictated by
     * {@link RetryHandler}. During retry, any errors that are retriable are consumed by the function and will not be observed
     * by the external {@link rx.Observer}. If number of retries exceeds the maximal retries allowed on one server, a final error will
     * be emitted by the returned {@link Observable}.
     *
     * @param forServer A lazy Observable that does not start execution until it is subscribed to
     */
    public Observable<T> retryWithSameServer(final Server server, final Observable<T> forServer) {
        return forServer.lift(new RetrySameServerOperator(server, forServer));
    }

    /**
     * Take a server and {@link com.netflix.loadbalancer.reactive.LoadBalancerExecutable} to execute the task
     * on the server with possible retries in blocking mode.
     */
    public T retryWithSameServer(final Server server, final LoadBalancerExecutable<T> executable) throws Exception {
        Observable<T> result = retryWithSameServer(server, toObsevable(executable).run(server));
        return RxUtils.getSingleValueWithRealErrorCause(result);
    }

    Observable<T> retryWithSameServer(final Server server, final Observable<T> forServer, int numberServersTried) {
        return forServer.lift(new RetrySameServerOperator(server, forServer, numberServersTried));
    }
}
