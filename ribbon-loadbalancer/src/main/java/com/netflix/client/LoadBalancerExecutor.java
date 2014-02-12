package com.netflix.client;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Observable.OnSubscribeFunc;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func1;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.utils.RxUtils;

public class LoadBalancerExecutor extends LoadBalancerContext {
    
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerExecutor.class);
    
    public LoadBalancerExecutor(ILoadBalancer lb) {
        super(lb);
    }
    
    public LoadBalancerExecutor(ILoadBalancer lb, IClientConfig clientConfig) {
        super(lb, clientConfig);
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
            return Observable.create(onsubscribe);
        }
    } 
    
    
    private class RetrySameServerFunc<T> implements Func1<Throwable, Observable<T>> {

        private final Server server;
        private final OnSubscribeFunc<T> onSubscribe;
        private final AtomicInteger counter = new AtomicInteger();
        private final RetryHandler callErrorHandler;

        public RetrySameServerFunc(Server server, OnSubscribeFunc<T> onSubscribe, RetryHandler errorHandler) {
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
            // URI uri = request.getUri();
            if (shouldRetry && !handleSameServerRetry(server, counter.incrementAndGet(), maxRetries, error)) {
                finalThrowable = new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
                        "NUMBEROFRETRIESEXEEDED:" + maxRetries + " retries, while making a call for: " + server, error);  
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

        private final URI requestedURI;
        private final AtomicInteger counter = new AtomicInteger();
        private final OnSubscribeFunc<T> onSubscribe;
        private final RetryHandler callErrorHandler;
        
        RetryNextServerFunc(URI requestedURI, OnSubscribeFunc<T> onSubscribe, RetryHandler errorHandler) {
            this.requestedURI = requestedURI;    
            this.onSubscribe = onSubscribe;
            this.callErrorHandler = errorHandler == null ? getErrorHandler() : errorHandler;
        }
        
        @Override
        public Observable<T> call(Throwable t1) {
            logger.debug("Get error during retry on next server", t1);   
            int maxRetriesNextServer = callErrorHandler.getMaxRetriesOnNextServer();
            boolean shouldRetry = maxRetriesNextServer > 0 && callErrorHandler.isRetriableException(t1, false);
            final Throwable finalThrowable;
            if (shouldRetry && counter.incrementAndGet() > maxRetriesNextServer) {
                finalThrowable = new ClientException(
                        ClientException.ErrorType.NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED,
                        "NUMBER_OF_RETRIES_NEXTSERVER_EXCEEDED :"
                                + maxRetriesNextServer
                                + " retries, while making a RestClient call for:"
                                + requestedURI + ":" +  getDeepestCause(t1).getMessage(), t1);
                shouldRetry = false;
            } else {
                finalThrowable = t1;
            }
            if (shouldRetry) {
                return Observable.create(onSubscribe).onErrorResumeNext(this);
            } else {
                return Observable.error(finalThrowable);
                
            }
        }
        
    }

    /**
     * Retry execution with load balancer with the given {@link ClientCallableProvider} that provides the logic to
     * execute network call synchronously with a given {@link Server}. 
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
    public <T> T retryWithLoadBalancer(final ClientCallableProvider<T> clientCallableProvider, @Nullable final URI loadBalancerURI, 
            @Nullable final RetryHandler retryHandler, @Nullable final Object loadBalancerKey) throws Exception {
        return RxUtils.getSingleValueWithRealErrorCause(
                retryWithLoadBalancer(CallableToObservable.toObsevableProvider(clientCallableProvider), loadBalancerURI, 
                retryHandler, loadBalancerKey));
    }
    
    /**
     * Create an {@link Observable} that retries execution with load balancer with the given {@link ClientObservableProvider} that provides the logic to
     * execute network call asynchronously with a given {@link Server}. 
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
    public <T> Observable<T> retryWithLoadBalancer(final ClientObservableProvider<T> clientObservableProvider, @Nullable final URI loadBalancerURI, 
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
        Observable<T> observable = Observable.create(onSubscribe);
        RetryNextServerFunc<T> retryNextServerFunc = new RetryNextServerFunc<T>(loadBalancerURI, onSubscribe, retryHandler);
        return observable.onErrorResumeNext(retryNextServerFunc);
    }
    
    protected <T> Observable<T> retrySameServer(final Server server, final ClientObservableProvider<T> clientObservableProvider, final RetryHandler errorHandler) {
        final ServerStats serverStats = getServerStats(server); 
        OnSubscribeFunc<T> onSubscribe = new OnSubscribeFunc<T>() {
            @Override
            public Subscription onSubscribe(final Observer<? super T> t1) {
                noteOpenConnection(serverStats);
                final Stopwatch tracer = getExecuteTracer().start();
                Observer<T> delegate = new Observer<T>() {
                    private volatile T entity; 
                    @Override
                    public void onCompleted() {
                        recordStats(entity, null);
                        t1.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        recordStats(entity, e);
                        t1.onError(e);
                    }

                    @Override
                    public void onNext(T args) {
                        entity = args;
                        t1.onNext(args);
                    }
                    
                    private void recordStats(Object entity, Throwable exception) {
                        tracer.stop();
                        long duration = tracer.getDuration(TimeUnit.MILLISECONDS);
                        noteRequestCompletion(serverStats, entity, exception, duration, errorHandler);
                    }
                };
                return clientObservableProvider.getObservableForEndpoint(server).subscribe(delegate);
            }
        };
        
        Observable<T> observable = Observable.create(onSubscribe);
        RetrySameServerFunc<T> retrySameServerFunc = new RetrySameServerFunc<T>(server, onSubscribe, errorHandler);
        return observable.onErrorResumeNext(retrySameServerFunc);
    }
}
