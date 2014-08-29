package com.netflix.loadbalancer;

import com.netflix.client.ClientException;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.servo.monitor.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.Operator;
import rx.Observer;
import rx.Subscriber;
import rx.observers.SafeSubscriber;
import rx.subscriptions.SerialSubscription;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Allen Wang
 */
public abstract class LoadBalancerCommand2<T> extends LoadBalancerContext implements LoadBalancerObservableCommand<T> {
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerCommand2.class);

    private final URI loadBalancerURI;
    private final Object loadBalancerKey;

    public LoadBalancerCommand2(ILoadBalancer lb) {
        this(lb, null, null, null, null);
    }

    public LoadBalancerCommand2(ILoadBalancer lb, IClientConfig clientConfig) {
        this(lb, clientConfig, null, null, null);
    }

    public LoadBalancerCommand2(ILoadBalancer lb, IClientConfig clientConfig, RetryHandler defaultRetryHandler) {
        this(lb, clientConfig, defaultRetryHandler, null, null);
    }

    public LoadBalancerCommand2(ILoadBalancer lb, IClientConfig clientConfig, RetryHandler defaultRetryHandler, URI loadBalancerURI, Object loadBalancerKey) {
        super(lb, clientConfig, defaultRetryHandler);
        this.loadBalancerURI = loadBalancerURI;
        this.loadBalancerKey = loadBalancerKey;
    }

    private class RetryNextServerOperator implements Operator<T, T> {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Subscriber<? super T> call(final Subscriber<? super T> t1) {
            SerialSubscription serialSubscription = new SerialSubscription();
            t1.add(serialSubscription);

            Subscriber<T> subscriber = new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    t1.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    logger.debug("Get error during retry on next server", t1);
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
                                        +  getDeepestCause(e).getMessage(), e);
                        shouldRetry = false;
                    } else {
                        finalThrowable = e;
                    }
                    if (shouldRetry) {
                        Server server = null;
                        try {
                            server = getServerFromLoadBalancer(loadBalancerURI, loadBalancerKey);
                        } catch (Exception ex) {
                            logger.error("Unexpected error", ex);
                            t1.onError(ex);
                        }
                        retryWithSameServer(server, run(server)).lift(RetryNextServerOperator.this).unsafeSubscribe(t1);
                    } else {
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
     *
     */
    public Observable<T> create() {
        Server server = null;
        try {
            server = getServerFromLoadBalancer(loadBalancerURI, loadBalancerKey);
        } catch (Exception e) {
            return Observable.error(e);
        }
        Observable<T> forSameServer = retryWithSameServer(server, this.run(server));
        // short cut: if no retry, return the same Observable
        if (getRetryHandler().getMaxRetriesOnNextServer() == 0) {
            return forSameServer;
        } else {
            return forSameServer.lift(new RetryNextServerOperator());
        }
    }

    private class RetrySameServerOperator implements Operator<T, T> {
        private final Server server;
        private final Observable<T> singleHostObservable;
        private final RetryHandler errorHandler = getRetryHandler();
        private final AtomicInteger counter = new AtomicInteger();

        RetrySameServerOperator(final Server server, final Observable<T> singleHostObservable) {
            this.server = server;
            this.singleHostObservable = singleHostObservable;
        }

        @Override
        public Subscriber<? super T> call(final Subscriber<? super T> t1) {
            SerialSubscription serialSubscription = new SerialSubscription();
            t1.add(serialSubscription);
            final ServerStats serverStats = getServerStats(server);
            noteOpenConnection(serverStats);
            final Stopwatch tracer = getExecuteTracer().start();
            Subscriber<T> subscriber = new Subscriber<T>() {
                private volatile T entity;
                @Override
                public void onCompleted() {
                    recordStats(entity, null);
                    t1.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    logger.debug("Got error {} when executed on server {}", e, server);
                    recordStats(entity, e);
                    int maxRetries = errorHandler.getMaxRetriesOnSameServer();
                    boolean shouldRetry = maxRetries > 0 && errorHandler.isRetriableException(e, true);
                    final Throwable finalThrowable;
                    if (shouldRetry && !handleSameServerRetry(server, counter.incrementAndGet(), maxRetries, e)) {
                        finalThrowable = new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
                                "Number of retries exceeded max " + maxRetries + " retries, while making a call for: " + server, e);
                        shouldRetry = false;
                    } else {
                        finalThrowable = e;
                    }

                    if (shouldRetry) {
                        singleHostObservable.lift(RetrySameServerOperator.this).unsafeSubscribe(t1);
                    } else {
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
                    noteRequestCompletion(serverStats, entity, exception, duration, errorHandler);
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
     * by the external {@link Observer}. If number of retries exceeds the maximal retries allowed on one server, a final error will
     * be emitted by the returned {@link Observable}.
     *
     * @param forServer A lazy Observable that does not start execution until it is subscribed to
     */
    public Observable<T> retryWithSameServer(final Server server, final Observable<T> forServer) {
        // return forServer;
        return forServer.lift(new RetrySameServerOperator(server, forServer));
    }

}
