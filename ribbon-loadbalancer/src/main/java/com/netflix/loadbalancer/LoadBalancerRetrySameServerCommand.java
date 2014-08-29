package com.netflix.loadbalancer;

import com.netflix.client.ClientException;
import com.netflix.client.RetryHandler;
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

/**
 * @author Allen Wang
 */
public class LoadBalancerRetrySameServerCommand<T> {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerCommand2.class);

    protected final LoadBalancerContext loadBalancerContext;
    private final RetryHandler retryHandler;

    public LoadBalancerRetrySameServerCommand(LoadBalancerContext loadBalancerContext) {
        this(loadBalancerContext, null);
    }

    public LoadBalancerRetrySameServerCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler) {
        this.loadBalancerContext = loadBalancerContext;
        this.retryHandler = retryHandler;
    }

    protected final RetryHandler getRetryHandler() {
        return retryHandler != null ? retryHandler : loadBalancerContext.getRetryHandler();
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
            final ServerStats serverStats = loadBalancerContext.getServerStats(server);
            loadBalancerContext.noteOpenConnection(serverStats);
            final Stopwatch tracer = loadBalancerContext.getExecuteTracer().start();
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
                    if (shouldRetry && !loadBalancerContext.handleSameServerRetry(server, counter.incrementAndGet(), maxRetries, e)) {
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
        // return forServer;
        return forServer.lift(new RetrySameServerOperator(server, forServer));
    }
}
