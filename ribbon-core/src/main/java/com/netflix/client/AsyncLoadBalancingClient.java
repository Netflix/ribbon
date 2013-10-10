package com.netflix.client;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.servo.monitor.Stopwatch;

public class AsyncLoadBalancingClient<T extends ClientRequest, S extends IResponse, U>
        extends LoadBalancerContext implements AsyncClient<T, S, U> {
    
    private AsyncClient<T, S, U> asyncClient;
    private static final Logger logger = LoggerFactory.getLogger(AsyncLoadBalancingClient.class);
    
    private LoadBalancerErrorHandler<? super T, ? super S> errorHandler = new DefaultLoadBalancerErrorHandler<ClientRequest, IResponse>();

    public AsyncLoadBalancingClient(AsyncClient<T, S, U> asyncClient) {
        super();
        this.asyncClient = asyncClient;
    }
    
    public AsyncLoadBalancingClient(AsyncClient<T, S, U> asyncClient, IClientConfig clientConfig) {
        super(clientConfig);
        this.asyncClient = asyncClient;
    }


    protected AsyncLoadBalancingClient() {
    }

    public final LoadBalancerErrorHandler<? super T, ? super S> getErrorHandler() {
        return errorHandler;
    }

    public final void setErrorHandler(LoadBalancerErrorHandler<T, S> errorHandler) {
        Preconditions.checkNotNull(errorHandler);
        this.errorHandler = errorHandler;
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
        
        private volatile Throwable exception = null;
        
        T getCompletedResponse() throws InterruptedException, ExecutionException {
            latch.await();
            if (completeResponse != null) {
                return completeResponse;
            } else if (exception != null) {
                throw new ExecutionException(exception);
            } else {
                throw new IllegalStateException("No response or exception is received");
            }
        }

        T getCompletedResponse(long time, TimeUnit timeUnit) throws InterruptedException, TimeoutException, ExecutionException {
            if (latch.await(time, timeUnit)) {
                if (completeResponse != null) {
                    return completeResponse;
                } else if (exception != null) {
                    throw new ExecutionException(exception);
                } else {
                    throw new IllegalStateException("No response or exception is received");
                }
            } else {
                throw new TimeoutException();
            }
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
            exception = e;
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

    public Future<S> execute(final T request, final FullResponseCallback<S> callback)
            throws ClientException {
        return execute(request, null, callback);
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
                boolean shouldRetry = retryOkayOnOperation && numRetriesNextServer > 0 && errorHandler.isRetriableException(request, e, false);
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
                    delegate.failed(e);
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
                if (errorHandler.isCircuitTrippingException(e) && serverStats != null) {
                    serverStats.incrementSuccessiveConnectionFailureCount();
                }
                boolean shouldRetry = retryOkayOnOperation && numRetries > 0 && errorHandler.isRetriableException(request, e, true);
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
                    callback.failed(e);
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
                callback.cancelled();
            }

            @Override
            public void responseReceived(S response) {
                if (errorHandler.isCircuitTrippinErrorgResponse(response)) {
                    serverStats.incrementSuccessiveConnectionFailureCount();
                }
                callback.responseReceived(response);
            }

            @Override
            public void contentReceived(E content) {
                callback.contentReceived(content);
            }            
        });
        currentRunningTask.set(future);
    }

    public <E> Future<S> parallelExecute(final T request, final StreamDecoder<E, U> decoder, final ResponseCallback<S, E> callback, final int numServers, long timeout, TimeUnit unit)
            throws ClientException {
        List<T> requests = Lists.newArrayList();
        for (int i = 0; i < numServers; i++) {
            requests.add(computeFinalUriWithLoadBalancer(request));
        }
        final LinkedBlockingDeque<Future<S>> results = new LinkedBlockingDeque<Future<S>>();
        final AtomicInteger failedCount = new AtomicInteger();
        final AtomicInteger finalSequenceNumber = new AtomicInteger(-1);
        final AtomicBoolean responseRecevied = new AtomicBoolean();
        final AtomicBoolean completedCalled = new AtomicBoolean();
        final AtomicBoolean failedCalled = new AtomicBoolean();
        final AtomicBoolean cancelledCalled = new AtomicBoolean();
        final Lock lock = new ReentrantLock();
        final Condition responseChosen = lock.newCondition();
        
        for (int i = 0; i < requests.size(); i++) {
            final int sequenceNumber = i;
            results.add(asyncClient.execute(requests.get(i), decoder, new ResponseCallback<S, E>() {
                private volatile boolean chosen = false;
                @Override
                public void completed(S response) {
                    if (completedCalled.compareAndSet(false, true)  
                            && callback != null && chosen) {
                        callback.completed(response);
                    }
                }

                @Override
                public void failed(Throwable e) {
                    int count = failedCount.incrementAndGet();
                    if ((count == numServers || chosen) && failedCalled.compareAndSet(false, true)) {
                        lock.lock();
                        try {
                            finalSequenceNumber.set(sequenceNumber);
                            responseChosen.signalAll();
                        } finally {
                            lock.unlock();
                        }
                        if (callback != null) {
                            callback.failed(e);
                        }
                    }
                }

                @Override
                public void cancelled() {
                    int count = failedCount.incrementAndGet();
                    if ((count == numServers || chosen) && cancelledCalled.compareAndSet(false, true)) {
                        lock.lock();
                        try {
                            finalSequenceNumber.set(sequenceNumber);
                            responseChosen.signalAll();
                        } finally {
                            lock.unlock();
                        }
                        if (callback != null) {
                            callback.cancelled();
                        }
                    }
                }

                @Override
                public void responseReceived(S response) {
                    if (responseRecevied.compareAndSet(false, true)) {
                        chosen = true;
                        lock.lock();
                        try {
                            finalSequenceNumber.set(sequenceNumber);
                            responseChosen.signalAll();
                        } finally {
                            lock.unlock();
                        }
                        if (callback != null) {
                            callback.responseReceived(response);
                        }
                    }
                    cancelOthers();
                }

                @Override
                public void contentReceived(E content) {
                    if (callback != null && chosen) {
                        callback.contentReceived(content);   
                    }
                }
                
                private void cancelOthers() {
                    int i = 0; 
                    for (Future<S> future: results) {
                        if (finalSequenceNumber.get() >= 0 && i != finalSequenceNumber.get() && !future.isCancelled()) {
                            try {
                                future.cancel(true);
                            } catch (Throwable e) {
                                logger.warn("Unable to cancel future", e);
                            }
                        }
                        i++;
                    }                    
                }
            }));
            lock.lock();
            try {
                if (finalSequenceNumber.get() >= 0 || responseChosen.await(timeout, unit)) {
                    // there is a response within the specified timeout, no need to send more requests
                    break;
                }
            } catch (InterruptedException e1) {
            } finally {
                lock.unlock();
            }
        }
        return new Future<S>() {
            private volatile boolean cancelled = false;
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled = true;
                for (Future<S> future: results) {
                    if (!future.isCancelled() && !future.cancel(mayInterruptIfRunning)) {
                            cancelled = false;
                    }
                }
                return cancelled;
            }
            
            @Override
            public S get() throws InterruptedException, ExecutionException {
                lock.lock();
                try {
                    while (finalSequenceNumber.get() < 0) {
                        responseChosen.await();
                    }
                } finally {
                    lock.unlock();
                }
                return Iterables.get(results, finalSequenceNumber.get()).get();
            }

            @Override
            public S get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException,
                    TimeoutException {
                lock.lock();
                long startTime = System.nanoTime();
                boolean elapsed = false;
                try {
                    if (finalSequenceNumber.get() < 0) {
                        elapsed = responseChosen.await(timeout, unit);
                    }
                } finally {
                    lock.unlock();
                }
                if (elapsed || finalSequenceNumber.get() < 0) {
                    throw new TimeoutException("No response is available yet from parallel execution");
                } else {
                    long timeWaited = System.nanoTime() - startTime;
                    long timeRemainingNanoSeconds = TimeUnit.NANOSECONDS.convert(timeout, unit) - timeWaited;
                    if (timeRemainingNanoSeconds > 0) {
                        return Iterables.get(results, finalSequenceNumber.get()).get(timeRemainingNanoSeconds, TimeUnit.NANOSECONDS);
                    } else {
                        throw new TimeoutException("No response is available yet from parallel execution");
                    }
                }                
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public boolean isDone() {
                if (finalSequenceNumber.get() < 0) {
                    return false;
                } else {
                    return Iterables.get(results, finalSequenceNumber.get()).isDone();
                }
            }
        };
    }
    
    @Override
    public void close() throws IOException {
        if (asyncClient != null) {
            asyncClient.close();
        }
    }
}
