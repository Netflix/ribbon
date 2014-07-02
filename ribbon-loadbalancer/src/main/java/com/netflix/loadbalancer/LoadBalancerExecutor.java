package com.netflix.loadbalancer;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observable.Operator;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;

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
    
    /**
     * Execute a task on a server chosen by load balancer with possible retries. If there are any errors that are indicated as 
     * retriable by the {@link RetryHandler}, they will be consumed internally. If number of retries has
     * exceeds the maximal allowed, a final error will be thrown. Otherwise, the first successful 
     * result during execution and retries will be returned. 
     * 
     * @param command interface that provides the logic to execute network call synchronously with a given {@link Server}
     * @throws Exception If any exception happens in the exception
     */
    public <T> T execute(final LoadBalancerCommand<T> command, RetryHandler retryHandler) throws Exception {
        return create(command, null, retryHandler, null);
    }
    
    /**
     * Execute a task on a server chosen by load balancer with possible retries. If there are any errors that are indicated as 
     * retriable by the {@link RetryHandler}, they will be consumed internally. If number of retries has
     * exceeds the maximal allowed, a final error will be thrown. Otherwise, the first successful 
     * result during execution and retries will be returned. 
     * 
     * @param command interface that provides the logic to execute network call synchronously with a given {@link Server}
     * @throws Exception If any exception happens in the exception
     */
    public <T> T execute(final LoadBalancerCommand<T> command) throws Exception {
        return create(command, null, null, null);
    }

    
    /**
     * Execute a task on a server chosen by load balancer with possible retries. If there are any errors that are indicated as 
     * retriable by the {@link RetryHandler}, they will be consumed internally. If number of retries has
     * exceeds the maximal allowed, a final error will be thrown. Otherwise, the first successful 
     * result during execution and retries will be returned. 
     * 
     * @param command interface that provides the logic to execute network call synchronously with a given {@link Server}
     * @param loadBalancerURI An optional URI that may contain a real host name and port to use as a fallback to the {@link LoadBalancerExecutor} 
     *                        if it does not have a load balancer or cannot find a server from its server list. For example, the URI contains
     *                        "www.google.com:80" will force the {@link LoadBalancerExecutor} to use www.google.com:80 as the actual server to
     *                        carry out the retry execution. See {@link LoadBalancerContext#getServerFromLoadBalancer(URI, Object)}
     * @param retryHandler  an optional handler to determine the retry logic of the {@link LoadBalancerExecutor}. If null, the default {@link RetryHandler}
     *                   of this {@link LoadBalancerExecutor} will be used.
     * @param loadBalancerKey An optional key passed to the load balancer to determine which server to return.
     * @throws Exception If any exception happens in the exception
     */
    protected <T> T create(final LoadBalancerCommand<T> command, @Nullable final URI loadBalancerURI, 
            @Nullable final RetryHandler retryHandler, @Nullable final Object loadBalancerKey) throws Exception {
        return RxUtils.getSingleValueWithRealErrorCause(
                create(CommandToObservableConverter.toObsevableCommand(command), loadBalancerURI, 
                retryHandler, loadBalancerKey));
    }
    
    /**
     * Create an {@link Observable} that once subscribed execute network call asynchronously with a server chosen by load balancer. 
     * If there are any errors that are indicated as retriable by the {@link RetryHandler}, they will be consumed internally by the
     * function and will not be observed by the {@link Observer} subscribed to the returned {@link Observable}. If number of retries has
     * exceeds the maximal allowed, a final error will be emitted by the returned {@link Observable}. Otherwise, the first successful 
     * result during execution and retries will be emitted. 
     * 
     * @param observableCommand interface that provides the logic to execute network call asynchronously with a given {@link Server}
     * @param retryHandler  an optional handler to determine the retry logic of the {@link LoadBalancerExecutor}. If null, the default {@link RetryHandler}
     *                   of this {@link LoadBalancerExecutor} will be used.
     */                   
    public <T> Observable<T> create(final LoadBalancerObservableCommand<T> observableCommand, @Nullable final RetryHandler retryHandler) {
        return create(observableCommand, null, retryHandler, null);
    }
    
    /**
     * Create an {@link Observable} that once subscribed execute network call asynchronously with a server chosen by load balancer. 
     * If there are any errors that are indicated as retriable by the {@link RetryHandler}, they will be consumed internally by the
     * function and will not be observed by the {@link Observer} subscribed to the returned {@link Observable}. If number of retries has
     * exceeds the maximal allowed, a final error will be emitted by the returned {@link Observable}. Otherwise, the first successful 
     * result during execution and retries will be emitted. 
     * 
     * @param observableCommand interface that provides the logic to execute network call synchronously with a given {@link Server}
     */
    public <T> Observable<T> create(final LoadBalancerObservableCommand<T> observableCommand) {
        return create(observableCommand, null, new DefaultLoadBalancerRetryHandler(), null);
    }

    private class RetryNextServerOperator<T> implements Operator<T, T> {
        private LoadBalancerObservableCommand<T> clientObservableProvider;
        private URI loadBalancerURI;
        private RetryHandler retryHandler;
        private Object loadBalancerKey;
        private final AtomicInteger counter = new AtomicInteger();

        public RetryNextServerOperator(final LoadBalancerObservableCommand<T> clientObservableProvider, @Nullable final URI loadBalancerURI, 
                @Nullable final RetryHandler retryHandler, @Nullable final Object loadBalancerKey) {
            this.clientObservableProvider = clientObservableProvider;
            this.loadBalancerURI = loadBalancerURI;
            this.retryHandler = retryHandler  == null ? getErrorHandler() : retryHandler;
            this.loadBalancerKey = loadBalancerKey;
        }
        
        @Override
        public Subscriber<? super T> call(final Subscriber<? super T> t1) {
            return new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    t1.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    logger.debug("Get error during retry on next server", t1);   
                    int maxRetriesNextServer = retryHandler.getMaxRetriesOnNextServer();
                    boolean sameServerRetryExceededLimit = (e instanceof ClientException) &&
                            ((ClientException) e).getErrorType().equals(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED);
                    boolean shouldRetry = maxRetriesNextServer > 0 && (sameServerRetryExceededLimit || retryHandler.isRetriableException(e, false));
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
                        retryWithSameServer(server, clientObservableProvider.run(server), retryHandler).lift(RetryNextServerOperator.this).unsafeSubscribe(t1);
                    } else {
                        t1.onError(finalThrowable);
                    }
                }

                @Override
                public void onNext(T t) {
                    t1.onNext(t);
                }
            };
        }
        
    }
    
    /**
     * Create an {@link Observable} that once subscribed execute network call asynchronously with a server chosen by load balancer. 
     * If there are any errors that are indicated as retriable by the {@link RetryHandler}, they will be consumed internally by the
     * function and will not be observed by the {@link Observer} subscribed to the returned {@link Observable}. If number of retries has
     * exceeds the maximal allowed, a final error will be emitted by the returned {@link Observable}. Otherwise, the first successful 
     * result during execution and retries will be emitted. 
     * 
     * @param observableCommand interface that provides the logic to execute network call asynchronously with a given {@link Server}
     * @param loadBalancerURI An optional URI that may contain a real host name and port to be used by {@link LoadBalancerExecutor} 
     *                        if it does not have a load balancer or cannot find a server from its server list. For example, the URI contains
     *                        "www.google.com:80" will force the {@link LoadBalancerExecutor} to use www.google.com:80 as the actual server to
     *                        carry out the retry execution. See {@link LoadBalancerContext#getServerFromLoadBalancer(URI, Object)}
     * @param retryHandler  an optional handler to determine the retry logic of the {@link LoadBalancerExecutor}. If null, the default {@link RetryHandler}
     *                   of this {@link LoadBalancerExecutor} will be used.
     * @param loadBalancerKey An optional key passed to the load balancer to determine which server to return.
     */
    protected <T> Observable<T> create(final LoadBalancerObservableCommand<T> observableCommand, @Nullable final URI loadBalancerURI, 
            @Nullable final RetryHandler retryHandler, @Nullable final Object loadBalancerKey) {
        return Observable.create(new OnSubscribe<T>(){
            @Override
            public void call(Subscriber<? super T> t1) {
              Server server = null;
              try {
                  server = getServerFromLoadBalancer(loadBalancerURI, loadBalancerKey);
              } catch (Exception e) {
                  logger.error("Unexpected error", e);
                  t1.onError(e);
              }
              retryWithSameServer(server, observableCommand.run(server), retryHandler)
                  .lift(new RetryNextServerOperator<T>(observableCommand, loadBalancerURI, retryHandler, loadBalancerKey))
                  .subscribe(t1);
            }
            
        });
    }
    
    private class RetrySameServerOperator<T> implements Operator<T, T> {
        private final Server server;
        private final Observable<T> singleHostObservable;
        private final RetryHandler errorHandler;
        private final AtomicInteger counter = new AtomicInteger();

        RetrySameServerOperator(final Server server, final Observable<T> singleHostObservable, final RetryHandler errorHandler) {
            this.server = server;
            this.singleHostObservable = singleHostObservable;
            this.errorHandler = errorHandler == null? getErrorHandler() : errorHandler;
        }
        
        @Override
        public Subscriber<? super T> call(final Subscriber<? super T> t1) {
            final ServerStats serverStats = getServerStats(server); 
            noteOpenConnection(serverStats);
            final Stopwatch tracer = getExecuteTracer().start();
            return new Subscriber<T>() {
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
    public <T> Observable<T> retryWithSameServer(final Server server, final Observable<T> forServer, final RetryHandler errorHandler) {
        return forServer.lift(new RetrySameServerOperator<T>(server, forServer, errorHandler));
    }
}
