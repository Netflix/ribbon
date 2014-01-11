package com.netflix.client.netty.http;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import rx.Observable;
import rx.Observable.OnSubscribeFunc;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func1;

import com.netflix.client.ClientRequest;
import com.netflix.client.IResponse;
import com.netflix.client.LoadBalancerContext;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.serialization.TypeDef;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;
import com.netflix.client.ClientException;

public class LoadBalancerObservables  extends LoadBalancerContext<HttpRequest, HttpResponse> {
    
    private RxNettyHttpClient client;
    
    public LoadBalancerObservables(IClientConfig clientConfig, RxNettyHttpClient client) {
        super(clientConfig);
        this.client = client;
    }
    
    private class RetrySameServerFunc<T> implements Func1<Throwable, Observable<T>> {

        private HttpRequest request;
        OnSubscribeFunc<T> onSubscribe;
        private AtomicInteger counter = new AtomicInteger();
        
        public RetrySameServerFunc(HttpRequest request, OnSubscribeFunc<T> onSubscribe) {
            this.request = request;
            this.onSubscribe = onSubscribe;
        }
        
        @Override
        public Observable<T> call(final Throwable error) {
            int maxRetries = getNumberRetriesOnSameServer(request.getOverrideConfig());
            boolean shouldRetry = isRetriable(request) && maxRetries > 0 && errorHandler.isRetriableException(request, error, true);
            final Throwable finalThrowable;
            URI uri = request.getUri();
            if (shouldRetry && !handleSameServerRetry(uri, counter.incrementAndGet(), maxRetries, error)) {
                finalThrowable = new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
                        "NUMBEROFRETRIESEXEEDED:" + maxRetries + " retries, while making a RestClient call for: " + uri, error);  
                shouldRetry = false;
            } else {
                finalThrowable = error;
            }
            
            if (shouldRetry) {
                // try again
                return Observable.create(onSubscribe).onErrorResumeNext(this);
            } else {
                return Observable.create(new OnSubscribeFunc<T>() {

                    @Override
                    public Subscription onSubscribe(Observer<? super T> t1) {
                        t1.onError(finalThrowable);
                        return Subscriptions.empty();
                    }
                });
            }
        }
    }

    private class RetryNextServerFunc<T> implements Func1<Throwable, Observable<T>> {

        HttpRequest request;
        private AtomicInteger counter = new AtomicInteger();
        OnSubscribeFunc<T> onSubscribe;
        
        RetryNextServerFunc(HttpRequest request, OnSubscribeFunc<T> onSubscribe) {
            this.request = request;    
            this.onSubscribe = onSubscribe;
        }
        @Override
        public Observable<T> call(Throwable t1) {
            int maxRetriesNextServer = getRetriesNextServer(request.getOverrideConfig());
            boolean shouldRetry = isRetriable(request) && maxRetriesNextServer > 0 && errorHandler.isRetriableException(request, t1, false);
            final Throwable finalThrowable;
            if (shouldRetry && counter.incrementAndGet() > maxRetriesNextServer) {
                finalThrowable = new ClientException(
                        ClientException.ErrorType.NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED,
                        "NUMBER_OF_RETRIES_NEXTSERVER_EXCEEDED :"
                                + maxRetriesNextServer
                                + " retries, while making a RestClient call for:"
                                + request.getUri() + ":" +  getDeepestCause(t1).getMessage(), t1);
                shouldRetry = false;
            } else {
                finalThrowable = t1;
            }
            if (shouldRetry) {
                return Observable.create(onSubscribe).onErrorResumeNext(this);
            } else {
                return Observable.create(new OnSubscribeFunc<T>() {
                    @Override
                    public Subscription onSubscribe(Observer<? super T> t1) {
                        t1.onError(finalThrowable);
                        return Subscriptions.empty();
                    }
                });
                
            }
        }
        
    }
    
    public <T> Observable<T> execute(final HttpRequest request, final TypeDef<T> typeDef) {
        OnSubscribeFunc<T> onSubscribe = new OnSubscribeFunc<T>() {
            @Override
            public Subscription onSubscribe(final Observer<? super T> t1) {
                HttpRequest requestWithRealServer = null;
                try {
                    requestWithRealServer = computeFinalUriWithLoadBalancer(request);
                } catch (Exception e) {
                    t1.onError(e);
                    return Subscriptions.empty();
                }
                return executeSameServer(requestWithRealServer, typeDef).subscribe(t1);
            }
        };
        Observable<T> observable = Observable.create(onSubscribe);
        RetryNextServerFunc<T> retryNextServerFunc = new RetryNextServerFunc<T>(request, onSubscribe);
        return observable.onErrorResumeNext(retryNextServerFunc);
    }
    
    public <T> Observable<T> executeSameServer(final HttpRequest request, final TypeDef<T> typeDef) {
        final URI uri = request.getUri();
        Server server = new Server(uri.getHost(), uri.getPort());
        final ServerStats serverStats = getServerStats(server); 
        OnSubscribeFunc<T> onSubscribe = new OnSubscribeFunc<T>() {
            @Override
            public Subscription onSubscribe(final Observer<? super T> t1) {
                noteOpenConnection(serverStats, request);
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
                        if (serverStats != null) {
                            serverStats.addToFailureCount();
                        }
                        if (errorHandler.isCircuitTrippingException(e) && serverStats != null) {
                            serverStats.incrementSuccessiveConnectionFailureCount();
                        }
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
                        noteRequestCompletion(serverStats, request, entity, exception, duration);
                    }
                };
                return client.execute(request, typeDef).subscribe(delegate);
            }
        };
        
        Observable<T> observable = Observable.create(onSubscribe);
        RetrySameServerFunc<T> retrySameServerFunc = new RetrySameServerFunc<T>(request, onSubscribe);
        return observable.onErrorResumeNext(retrySameServerFunc);
    }
}
