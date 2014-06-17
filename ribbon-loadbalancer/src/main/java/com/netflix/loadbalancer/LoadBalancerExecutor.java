package com.netflix.loadbalancer;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;

import com.netflix.client.ClientException;
import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.utils.RxUtils;

/**
 * Provides APIs to execute and retry tasks on a server chosen by the associated load balancer. 
 * With appropriate {@link RetryHandler}, it will also retry on one or more different servers.
 * 
 * 
 * @author awang
 *
 */
public class LoadBalancerExecutor extends LoadBalancerContext {
    
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerExecutor.class);
    
    static interface OnSubscribeFunc<T> {
        public Subscription onSubscribe(final Observer<? super T> t1);
    }

    public LoadBalancerExecutor(ILoadBalancer lb) {
        super(lb);
    }
    
    public LoadBalancerExecutor(ILoadBalancer lb, IClientConfig clientConfig) {
        super(lb, clientConfig);
    }
    
    public LoadBalancerExecutor(ILoadBalancer lb, IClientConfig clientConfig, RetryHandler defaultRetryHandler) {
        super(lb, clientConfig, defaultRetryHandler);
    }
    
    public static class CallableToObservable<T> implements ClientObservableProvider<T> {

        private final ClientCallableProvider<T> callableProvider;
        
        public static <T> ClientObservableProvider<T> toObsevableProvider(ClientCallableProvider<T> callableProvider) {
            return new CallableToObservable<T>(callableProvider);
        }
        
        public CallableToObservable(ClientCallableProvider<T> callableProvider) {
            this.callableProvider = callableProvider;
        }
        
        @Override
        public Observable<T> getObservableForEndpoint(final Server server) {
            OnSubscribeFunc<T> onsubscribe = new OnSubscribeFunc<T>() {
                @Override
                public Subscription onSubscribe(Observer<? super T> t1) {
                    try {
                        T obj = callableProvider.executeOnServer(server);
                        t1.onNext(obj);
                        t1.onCompleted();
                    } catch (Exception e) {
                        t1.onError(e);
                    }
                    return Subscriptions.empty();
                };
            };
            return createObservableFromOnSubscribeFunc(onsubscribe);
        }
    } 
    
    private class RetrySameServerFunc<T> implements Func1<Throwable, Observable<T>> {

        private final Server server;
        private final OnSubscribe<T> onSubscribe;
        private final AtomicInteger counter = new AtomicInteger();
        private final RetryHandler callErrorHandler;

        public RetrySameServerFunc(Server server, OnSubscribe<T> onSubscribe, RetryHandler errorHandler) {
            this.server = server;
            this.onSubscribe = onSubscribe;
            this.callErrorHandler = errorHandler == null ? getErrorHandler() : errorHandler;
        }
        
        @Override
        public Observable<T> call(final Throwable error) {
            logger.debug("Get error during retry on same server", error);
            int maxRetries = callErrorHandler.getMaxRetriesOnSameServer();
            boolean shouldRetry = maxRetries > 0 && callErrorHandler.isRetriableException(error, true);
            final Throwable finalThrowable;
            if (shouldRetry && !handleSameServerRetry(server, counter.incrementAndGet(), maxRetries, error)) {
                finalThrowable = new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
                        "Number of retries exceeded max " + maxRetries + " retries, while making a call for: " + server, error);  
                shouldRetry = false;
            } else {
                finalThrowable = error;
            }
            
            if (shouldRetry) {
                // try again
                return Observable.create(onSubscribe).onErrorResumeNext(this);
            } else {
                return Observable.error(finalThrowable);
            }
        }
    }

    private class RetryNextServerFunc<T> implements Func1<Throwable, Observable<T>> {

        private final AtomicInteger counter = new AtomicInteger();
        private final OnSubscribeFunc<T> onSubscribe;
        private final RetryHandler callErrorHandler;
        
        RetryNextServerFunc(OnSubscribeFunc<T> onSubscribe, RetryHandler errorHandler) {
            this.onSubscribe = onSubscribe;
            this.callErrorHandler = errorHandler == null ? getErrorHandler() : errorHandler;
        }
        
        @Override
        public Observable<T> call(Throwable t1) {
            logger.debug("Get error during retry on next server", t1);   
            int maxRetriesNextServer = callErrorHandler.getMaxRetriesOnNextServer();
            boolean sameServerRetryExceededLimit = (t1 instanceof ClientException) &&
                    ((ClientException) t1).getErrorType().equals(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED);
            boolean shouldRetry = maxRetriesNextServer > 0 && (sameServerRetryExceededLimit || callErrorHandler.isRetriableException(t1, false));
            final Throwable finalThrowable;
            if (shouldRetry && counter.incrementAndGet() > maxRetriesNextServer) {
                finalThrowable = new ClientException(
                        ClientException.ErrorType.NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED,
                        "NUMBER_OF_RETRIES_NEXTSERVER_EXCEEDED :"
                                + maxRetriesNextServer
                                + " retries, while making a call with load balancer: "
                                +  getDeepestCause(t1).getMessage(), t1);
                shouldRetry = false;
            } else {
                finalThrowable = t1;
            }
            if (shouldRetry) {
                return createObservableFromOnSubscribeFunc(onSubscribe).onErrorResumeNext(this);
            } else {
                return Observable.error(finalThrowable);
                
            }
        }
        
    }

    
    /**
     * Execute a task on a server chosen by load balancer with possible retries. If there are any errors that are indicated as 
     * retriable by the {@link RetryHandler}, they will be consumed internally. If number of retries has
     * exceeds the maximal allowed, a final error will be thrown. Otherwise, the first successful 
     * result during execution and retries will be returned. 
     * 
     * @param clientCallableProvider interface that provides the logic to execute network call synchronously with a given {@link Server}
     * @throws Exception If any exception happens in the exception
     */
    public <T> T executeWithLoadBalancer(final ClientCallableProvider<T> clientCallableProvider, RetryHandler retryHandler) throws Exception {
        return executeWithLoadBalancer(clientCallableProvider, null, retryHandler, null);
    }
    
    /**
     * Execute a task on a server chosen by load balancer with possible retries. If there are any errors that are indicated as 
     * retriable by the {@link RetryHandler}, they will be consumed internally. If number of retries has
     * exceeds the maximal allowed, a final error will be thrown. Otherwise, the first successful 
     * result during execution and retries will be returned. 
     * 
     * @param clientCallableProvider interface that provides the logic to execute network call synchronously with a given {@link Server}
     * @throws Exception If any exception happens in the exception
     */
    public <T> T executeWithLoadBalancer(final ClientCallableProvider<T> clientCallableProvider) throws Exception {
        return executeWithLoadBalancer(clientCallableProvider, null, null, null);
    }

    
    /**
     * Execute a task on a server chosen by load balancer with possible retries. If there are any errors that are indicated as 
     * retriable by the {@link RetryHandler}, they will be consumed internally. If number of retries has
     * exceeds the maximal allowed, a final error will be thrown. Otherwise, the first successful 
     * result during execution and retries will be returned. 
     * 
     * @param clientCallableProvider interface that provides the logic to execute network call synchronously with a given {@link Server}
     * @param loadBalancerURI An optional URI that may contain a real host name and port to use as a fallback to the {@link LoadBalancerExecutor} 
     *                        if it does not have a load balancer or cannot find a server from its server list. For example, the URI contains
     *                        "www.google.com:80" will force the {@link LoadBalancerExecutor} to use www.google.com:80 as the actual server to
     *                        carry out the retry execution. See {@link LoadBalancerContext#getServerFromLoadBalancer(URI, Object)}
     * @param retryHandler  an optional handler to determine the retry logic of the {@link LoadBalancerExecutor}. If null, the default {@link RetryHandler}
     *                   of this {@link LoadBalancerExecutor} will be used.
     * @param loadBalancerKey An optional key passed to the load balancer to determine which server to return.
     * @throws Exception If any exception happens in the exception
     */
    protected <T> T executeWithLoadBalancer(final ClientCallableProvider<T> clientCallableProvider, @Nullable final URI loadBalancerURI, 
            @Nullable final RetryHandler retryHandler, @Nullable final Object loadBalancerKey) throws Exception {
        return RxUtils.getSingleValueWithRealErrorCause(
                executeWithLoadBalancer(CallableToObservable.toObsevableProvider(clientCallableProvider), loadBalancerURI, 
                retryHandler, loadBalancerKey));
    }
    
    /**
     * Create an {@link Observable} that once subscribed execute network call asynchronously with a server chosen by load balancer. 
     * If there are any errors that are indicated as retriable by the {@link RetryHandler}, they will be consumed internally by the
     * function and will not be observed by the {@link Observer} subscribed to the returned {@link Observable}. If number of retries has
     * exceeds the maximal allowed, a final error will be emitted by the returned {@link Observable}. Otherwise, the first successful 
     * result during execution and retries will be emitted. 
     * 
     * @param clientObservableProvider interface that provides the logic to execute network call asynchronously with a given {@link Server}
     * @param retryHandler  an optional handler to determine the retry logic of the {@link LoadBalancerExecutor}. If null, the default {@link RetryHandler}
     *                   of this {@link LoadBalancerExecutor} will be used.
     */                   
    public <T> Observable<T> executeWithLoadBalancer(final ClientObservableProvider<T> clientObservableProvider, @Nullable final RetryHandler retryHandler) {
        return executeWithLoadBalancer(clientObservableProvider, null, retryHandler, null);
    }
    
    /**
     * Create an {@link Observable} that once subscribed execute network call asynchronously with a server chosen by load balancer. 
     * If there are any errors that are indicated as retriable by the {@link RetryHandler}, they will be consumed internally by the
     * function and will not be observed by the {@link Observer} subscribed to the returned {@link Observable}. If number of retries has
     * exceeds the maximal allowed, a final error will be emitted by the returned {@link Observable}. Otherwise, the first successful 
     * result during execution and retries will be emitted. 
     * 
     * @param clientObservableProvider interface that provides the logic to execute network call synchronously with a given {@link Server}
     */
    public <T> Observable<T> executeWithLoadBalancer(final ClientObservableProvider<T> clientObservableProvider) {
        return executeWithLoadBalancer(clientObservableProvider, null, new DefaultLoadBalancerRetryHandler(), null);
    }

    
    /**
     * Create an {@link Observable} that once subscribed execute network call asynchronously with a server chosen by load balancer. 
     * If there are any errors that are indicated as retriable by the {@link RetryHandler}, they will be consumed internally by the
     * function and will not be observed by the {@link Observer} subscribed to the returned {@link Observable}. If number of retries has
     * exceeds the maximal allowed, a final error will be emitted by the returned {@link Observable}. Otherwise, the first successful 
     * result during execution and retries will be emitted. 
     * 
     * @param clientObservableProvider interface that provides the logic to execute network call asynchronously with a given {@link Server}
     * @param loadBalancerURI An optional URI that may contain a real host name and port to be used by {@link LoadBalancerExecutor} 
     *                        if it does not have a load balancer or cannot find a server from its server list. For example, the URI contains
     *                        "www.google.com:80" will force the {@link LoadBalancerExecutor} to use www.google.com:80 as the actual server to
     *                        carry out the retry execution. See {@link LoadBalancerContext#getServerFromLoadBalancer(URI, Object)}
     * @param retryHandler  an optional handler to determine the retry logic of the {@link LoadBalancerExecutor}. If null, the default {@link RetryHandler}
     *                   of this {@link LoadBalancerExecutor} will be used.
     * @param loadBalancerKey An optional key passed to the load balancer to determine which server to return.
     */
    protected <T> Observable<T> executeWithLoadBalancer(final ClientObservableProvider<T> clientObservableProvider, @Nullable final URI loadBalancerURI, 
            @Nullable final RetryHandler retryHandler, @Nullable final Object loadBalancerKey) {
        OnSubscribeFunc<T> onSubscribe = new OnSubscribeFunc<T>() {
            @Override
            public Subscription onSubscribe(final Observer<? super T> t1) {
                Server server = null;
                try {
                    server = getServerFromLoadBalancer(loadBalancerURI, loadBalancerKey);
                } catch (Exception e) {
                    logger.error("Unexpected error", e);
                    t1.onError(e);
                    return Subscriptions.empty();
                }
                return retrySameServer(server, clientObservableProvider, retryHandler).subscribe(t1);
            }
        };
        Observable<T> observable = createObservableFromOnSubscribeFunc(onSubscribe);
        RetryNextServerFunc<T> retryNextServerFunc = new RetryNextServerFunc<T>(onSubscribe, retryHandler);
        return observable.onErrorResumeNext(retryNextServerFunc);
    }
    
    /**
     * Gets the {@link Observable} that represents the result of executing on a server, after possible retries as dictated by 
     * {@link RetryHandler}. During retry, any errors that are retriable are consumed by the function and will not be observed
     * by the external {@link Observer}. If number of retries exceeds the maximal retries allowed on one server, a final error will 
     * be emitted by the returned {@link Observable}.
     */
    protected <T> Observable<T> retrySameServer(final Server server, final ClientObservableProvider<T> clientObservableProvider, final RetryHandler errorHandler) {
        OnSubscribe<T> onSubscribe = new OnSubscribe<T>() {
            @Override
            public void call(final Subscriber<? super T> t1) {
                final ServerStats serverStats = getServerStats(server); 
                noteOpenConnection(serverStats);
                final Stopwatch tracer = getExecuteTracer().start();
                /*
                 * A delegate Observer that observes the execution result
                 * and records load balancer related statistics before 
                 * sending the same result to the external Observer 
                 */
                Observer<T> delegate = new Observer<T>() {
                    private volatile T entity; 
                    @Override
                    public void onCompleted() {
                        recordStats(entity, null);
                        t1.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.debug("Got error %s when executed on server %s", e, server);
                        recordStats(entity, e);
                        t1.onError(e);
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
                clientObservableProvider.getObservableForEndpoint(server).subscribe(delegate);
            }
        };
        
        Observable<T> observable = Observable.create(onSubscribe);
        RetrySameServerFunc<T> retrySameServerFunc = new RetrySameServerFunc<T>(server, onSubscribe, errorHandler);
        return observable.onErrorResumeNext(retrySameServerFunc);
    }
    
    private static <T> Observable<T> createObservableFromOnSubscribeFunc(final OnSubscribeFunc<T> onSubscribe) {
        return Observable.create(new OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> observer) {
                onSubscribe.onSubscribe(observer);
            }
        });
    }
}
