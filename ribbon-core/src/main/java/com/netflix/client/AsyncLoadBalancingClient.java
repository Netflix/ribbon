package com.netflix.client;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.servo.monitor.Stopwatch;

public class AsyncLoadBalancingClient<T extends ClientRequest, S extends IResponse, U>
        extends LoadBalancerContext implements AsyncClient<T, S, U> {
    
    private AsyncClient<T, S, U> asyncClient;
    private static final Logger logger = LoggerFactory.getLogger(AsyncLoadBalancingClient.class);


    public AsyncLoadBalancingClient(AsyncClient<T, S, U> asyncClient) {
        super();
        this.asyncClient = asyncClient;
    }

    protected AsyncLoadBalancingClient() {
    }

    
    private Future<S> getFuture(final AtomicReference<Future<S>> futurePointer, final CallbackDelegate<S, ?> callbackDelegate) {
        return new Future<S>() {

            @Override
            public boolean cancel(boolean arg0) {
                Future<S> current = futurePointer.get();
                if (current != null) {                    
                    return current.cancel(arg0);
                } else {
                    return false;
                }
            }

            @Override
            public S get() throws InterruptedException, ExecutionException {
                return callbackDelegate.getCompletedResponse();
            }

            @Override
            public S get(long arg0, TimeUnit arg1)
                    throws InterruptedException, ExecutionException,
                    TimeoutException {
                return callbackDelegate.getCompletedResponse(arg0,  arg1);
            }

            @Override
            public boolean isCancelled() {
                Future<S> current = futurePointer.get();
                if (current != null) {                    
                    return current.isCancelled();
                } else {
                    return false;
                }
            }

            @Override
            public boolean isDone() {
                return callbackDelegate.isDone();
            }
            
        };
        
    }
    
    private static class CallbackDelegate<T extends IResponse, E> implements ResponseCallback<T, E> {

        private ResponseCallback<T, E> callback;

        public CallbackDelegate(ResponseCallback<T, E> callback) {
            this.callback = callback;
        }
        
        private CountDownLatch latch = new CountDownLatch(1);
        private volatile T completeResponse = null; 
        
        T getCompletedResponse() throws InterruptedException {
            latch.await();
            return completeResponse;
        }

        T getCompletedResponse(long time, TimeUnit timeUnit) throws InterruptedException {
            latch.await(time, timeUnit);
            return completeResponse;
        }
               
        boolean isDone() {
            return latch.getCount() <= 0;
        }

        @Override
        public void completed(T response) {
            latch.countDown();
            completeResponse = response;
            if (callback != null) {
                callback.completed(response);
            }
        }

        @Override
        public void failed(Throwable e) {
            latch.countDown();
            if (callback != null) {
                callback.failed(e);
            }
        }

        @Override
        public void cancelled() {
            latch.countDown();
            if (callback != null) {
                callback.cancelled();
            }
        }

        @Override
        public void responseReceived(T response) {
            if (callback != null) {
                callback.responseReceived(response);
            }
        }

        @Override
        public void contentReceived(E content) {
            if (callback != null) {
                callback.contentReceived(content);
            }
        }
    }
    
    @Override
    public <E> Future<S> execute(final T request, final StreamDecoder<E, U> decoder, final ResponseCallback<S, E> callback)
            throws ClientException {
        final AtomicInteger retries = new AtomicInteger(0);
        final boolean retryOkayOnOperation = isRetriable(request);

        final int numRetriesNextServer = getRetriesNextServer(request.getOverrideConfig()); 
        T resolved = computeFinalUriWithLoadBalancer(request);
        
        final CallbackDelegate<S, E> delegate = new CallbackDelegate<S, E>(callback);
        final AtomicReference<Future<S>> currentRunningTask = new AtomicReference<Future<S>>();
        
        asyncExecuteOnSingleServer(resolved, decoder, new ResponseCallback<S, E>() {

            @Override
            public void completed(S response) {
                delegate.completed(response);
            }

            @Override
            public void failed(Throwable e) {
                boolean shouldRetry = false;
                if (e instanceof ClientException) {
                    // we dont want to retry for PUT/POST and DELETE, we can for GET
                    shouldRetry = retryOkayOnOperation && numRetriesNextServer > 0;
                }
                if (shouldRetry) {
                    if (retries.incrementAndGet() > numRetriesNextServer) {
                        delegate.failed(new ClientException(
                                ClientException.ErrorType.NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED,
                                "NUMBER_OF_RETRIES_NEXTSERVER_EXCEEDED :"
                                + numRetriesNextServer
                                + " retries, while making a RestClient call for:"
                                + request.getUri() + ":" +  getDeepestCause(e).getMessage(), e));
                        return;
                    }
                    logger.error("Exception while executing request which is deemed retry-able, retrying ..., Next Server Retry Attempt#:"
                            + retries
                            + ", URI tried:"
                            + request.getUri());
                    try {
                        asyncExecuteOnSingleServer(computeFinalUriWithLoadBalancer(request), decoder, this, currentRunningTask);
                    } catch (ClientException e1) {
                        delegate.failed(e1);
                    }
                } else {
                    if (e instanceof ClientException) {
                        delegate.failed(e);
                    } else {
                        delegate.failed(new ClientException(
                                ClientException.ErrorType.GENERAL,
                                "Unable to execute request for URI:" + request.getUri(),
                                e));
                    }
                }
            }

            @Override
            public void cancelled() {
                delegate.cancelled();
            }

            @Override
            public void responseReceived(S response) {
                delegate.responseReceived(response);
            }

            @Override
            public void contentReceived(E content) {
                delegate.contentReceived(content);
            }
            
        }, currentRunningTask);
        return getFuture(currentRunningTask, delegate);
    }

    /**
     * Execute the request on single server after the final URI is calculated. This method takes care of
     * retries and update server stats.
     * @throws ClientException 
     *  
     */
    protected <E> void asyncExecuteOnSingleServer(final T request, final StreamDecoder<E, U> decoder, 
            final ResponseCallback<S, E> callback, final AtomicReference<Future<S>> currentRunningTask) throws ClientException {
        final AtomicInteger retries = new AtomicInteger(0);

        final boolean retryOkayOnOperation = request.isRetriable()? true: okToRetryOnAllOperations;
        final int numRetries = getNumberRetriesOnSameServer(request.getOverrideConfig());
        final URI uri = request.getUri();
        Server server = new Server(uri.getHost(), uri.getPort());
        final ServerStats serverStats = getServerStats(server);
        final Stopwatch tracer = getExecuteTracer().start();
        noteOpenConnection(serverStats, request);
        Future<S> future = asyncClient.execute(request, decoder, new ResponseCallback<S, E>() {
            private S thisResponse;
            private Throwable thisException;
            @Override
            public void completed(S response) {
                thisResponse = response;
                onComplete();
                callback.completed(response);
            }

            @Override
            public void failed(Throwable e) {
                thisException = e;
                onComplete();
                if (serverStats != null) {
                    serverStats.addToFailureCount();
                }
                if (isCircuitBreakerException(e) && serverStats != null) {
                    serverStats.incrementSuccessiveConnectionFailureCount();
                }
                boolean shouldRetry = retryOkayOnOperation && numRetries > 0 && isRetriableException(e);
                if (shouldRetry) {
                    if (!handleSameServerRetry(uri, retries.incrementAndGet(), numRetries, e)) {
                        callback.failed(new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
                                "NUMBEROFRETRIESEXEEDED :" + numRetries + " retries, while making a RestClient call for: " + uri, e));                        
                    } else {
                        tracer.start();
                        noteOpenConnection(serverStats, request);
                        try {
                            Future<S> future = asyncClient.execute(request, decoder, this);
                            currentRunningTask.set(future);
                        } catch (ClientException ex) {
                            callback.failed(ex);
                        }
                    } 
                } else {
                    ClientException clientException = generateNIWSException(uri.toString(), e);
                    callback.failed(clientException);
                }
            }
            
            private void onComplete() {
                tracer.stop();
                long duration = tracer.getDuration(TimeUnit.MILLISECONDS);
                noteRequestCompletion(serverStats, request, thisResponse, thisException, duration);
            }

            @Override
            public void cancelled() {
                onComplete();
            }

            @Override
            public void responseReceived(S response) {
                callback.responseReceived(response);
            }

            @Override
            public void contentReceived(E content) {
                callback.contentReceived(content);
            }            
        });
        currentRunningTask.set(future);
    }

    
    @Override
    protected boolean isCircuitBreakerException(Throwable e) {
        return true;
    }

    @Override
    protected boolean isRetriableException(Throwable e) {
        return true;
    }
}
