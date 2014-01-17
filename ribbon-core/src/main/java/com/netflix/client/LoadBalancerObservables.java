package com.netflix.client;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import rx.Observable;
import rx.Observable.OnSubscribeFunc;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func1;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.servo.monitor.Stopwatch;

public class LoadBalancerObservables<R extends ClientRequest, S extends IResponse> extends LoadBalancerContext<R, S> {
    
    public LoadBalancerObservables(IClientConfig clientConfig) {
        super(clientConfig);
    }
    
    private class RetrySameServerFunc<T> implements Func1<Throwable, Observable<T>> {

        private R request;
        OnSubscribeFunc<T> onSubscribe;
        private AtomicInteger counter = new AtomicInteger();
        
        public RetrySameServerFunc(R request, OnSubscribeFunc<T> onSubscribe) {
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
                return Observable.error(finalThrowable);
            }
        }
    }

    private class RetryNextServerFunc<T> implements Func1<Throwable, Observable<T>> {

        R request;
        private AtomicInteger counter = new AtomicInteger();
        OnSubscribeFunc<T> onSubscribe;
        
        RetryNextServerFunc(R request, OnSubscribeFunc<T> onSubscribe) {
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
                return Observable.error(finalThrowable);
                
            }
        }
        
    }
    
    public <T> Observable<T> retryWithLoadBalancer(final R request, final LoadBalancerObservableRequest<T, R> loadBalancerRequest) {
        OnSubscribeFunc<T> onSubscribe = new OnSubscribeFunc<T>() {
            @Override
            public Subscription onSubscribe(final Observer<? super T> t1) {
                R requestWithRealServer = null;
                try {
                    requestWithRealServer = computeFinalUriWithLoadBalancer(request);
                } catch (Exception e) {
                    t1.onError(e);
                    return Subscriptions.empty();
                }
                return retrySameServer(requestWithRealServer, loadBalancerRequest.getSingleServerObservable(requestWithRealServer)).subscribe(t1);
            }
        };
        Observable<T> observable = Observable.create(onSubscribe);
        RetryNextServerFunc<T> retryNextServerFunc = new RetryNextServerFunc<T>(request, onSubscribe);
        return observable.onErrorResumeNext(retryNextServerFunc);
    }
    
    public <T> Observable<T> retrySameServer(final R request, final Observable<T> clientObservable) {
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
                return clientObservable.subscribe(delegate);
            }
        };
        
        Observable<T> observable = Observable.create(onSubscribe);
        RetrySameServerFunc<T> retrySameServerFunc = new RetrySameServerFunc<T>(request, onSubscribe);
        return observable.onErrorResumeNext(retrySameServerFunc);
    }
}
