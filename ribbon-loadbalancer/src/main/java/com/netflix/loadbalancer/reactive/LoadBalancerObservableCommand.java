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
import com.netflix.client.Utils;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.Operator;
import rx.Observer;
import rx.Subscriber;
import rx.subscriptions.SerialSubscription;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A command that is used to produce the Observable from the load balancer execution. The load balancer is responsible for
 * the following:
 *
 * <ul>
 * <li>Choose a server</li>
 * <li>Invoke the {@link #call(com.netflix.loadbalancer.Server)} method</li>
 * <li>Invoke the {@link ExecutionListener} if any</li>
 * <li>Retry on exception, controlled by {@link com.netflix.client.RetryHandler}</li>
 * <li>Provide feedback to the {@link com.netflix.loadbalancer.LoadBalancerStats}</li>
 * </ul>
 *
 * @author Allen Wang
 */
public abstract class LoadBalancerObservableCommand<T> extends LoadBalancerRetrySameServerCommand<T> implements LoadBalancerObservable<T> {
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerObservableCommand.class);

    private final URI loadBalancerURI;
    private final Object loadBalancerKey;

    public LoadBalancerObservableCommand(LoadBalancerContext loadBalancerContext) {
        this(loadBalancerContext, null, null, null, null);
    }

    public LoadBalancerObservableCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler) {
        this(loadBalancerContext, retryHandler, null, null, null);
    }

    public LoadBalancerObservableCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler, ExecutionContextListenerInvoker<?, T> listenerInvoker) {
        this(loadBalancerContext, retryHandler, null, null, listenerInvoker);
    }

    public LoadBalancerObservableCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler, URI loadBalancerURI, Object loadBalancerKey, ExecutionContextListenerInvoker<?, T> listenerInvoker) {
        super(loadBalancerContext, retryHandler, listenerInvoker);
        this.loadBalancerURI = loadBalancerURI;
        this.loadBalancerKey = loadBalancerKey;
    }

    private class RetryNextServerOperator implements Operator<T, T> {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Subscriber<? super T> call(final Subscriber<? super T> t1) {
            if (listenerInvoker != null && counter.get() == 0) {
                try {
                    listenerInvoker.onExecutionStart();
                } catch (AbortExecutionException e) {
                    throw e;
                }
            }
            SerialSubscription serialSubscription = new SerialSubscription();
            t1.add(serialSubscription);

            Subscriber<T> subscriber = new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    t1.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    logger.debug("Get error during retry on next server", e);
                    if (e instanceof AbortExecutionException) {
                        t1.onError(e);
                        return;
                    }
                    int maxRetriesNextServer = getRetryHandler().getMaxRetriesOnNextServer();
                    boolean sameServerRetryExceededLimit = (e instanceof ClientException) &&
                            ((ClientException) e).getErrorType().equals(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED);
                    boolean shouldRetry = maxRetriesNextServer > 0 && (sameServerRetryExceededLimit || getRetryHandler().isRetriableException(e, false));
                    final Throwable finalThrowable;
                    if (shouldRetry && counter.incrementAndGet() > maxRetriesNextServer) {
                        finalThrowable = new ClientException(
                                ClientException.ErrorType.NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED,
                                "NUMBER_OF_RETRIES_NEXTSERVER_EXCEEDED :"
                                        + maxRetriesNextServer
                                        + " retries, while making a call with load balancer: "
                                        +  Utils.getDeepestCause(e).getMessage(), e);
                        shouldRetry = false;
                    } else {
                        finalThrowable = e;
                    }
                    if (shouldRetry) {
                        Server server = null;
                        try {
                            server = loadBalancerContext.getServerFromLoadBalancer(loadBalancerURI, loadBalancerKey);
                        } catch (Exception ex) {
                            logger.error("Unexpected error", ex);
                            t1.onError(ex);
                        }
                        retryWithSameServer(server, LoadBalancerObservableCommand.this.call(server), counter.get()).lift(RetryNextServerOperator.this).unsafeSubscribe(t1);
                    } else {
                        if (listenerInvoker != null) {
                            listenerInvoker.onExecutionFailed(finalThrowable, executionInfo);
                        }
                        t1.onError(finalThrowable);
                    }
                }

                @Override
                public void onNext(T t) {
                    t1.onNext(t);
                }
            };
            serialSubscription.set(subscriber);
            return subscriber;
        }

    }

    /**
     * Create an {@link Observable} that once subscribed execute network call asynchronously with a server chosen by load balancer.
     * If there are any errors that are indicated as retriable by the {@link RetryHandler}, they will be consumed internally by the
     * function and will not be observed by the {@link Observer} subscribed to the returned {@link Observable}. If number of retries has
     * exceeds the maximal allowed, a final error will be emitted by the returned {@link Observable}. Otherwise, the first successful
     * result during execution and retries will be emitted.
     */
    public Observable<T> toObservable() {
        Server server = null;
        try {
            server = loadBalancerContext.getServerFromLoadBalancer(loadBalancerURI, loadBalancerKey);
        } catch (Exception e) {
            return Observable.error(e);
        }
        if (getRetryHandler().getMaxRetriesOnNextServer() == 0) {
            // short cut: if no retry, return the same Observable
            return retryWithSameServer(server, this.call(server));
        } else {
            return retryWithSameServer(server, this.call(server), 0).lift(new RetryNextServerOperator());
        }
    }

}
