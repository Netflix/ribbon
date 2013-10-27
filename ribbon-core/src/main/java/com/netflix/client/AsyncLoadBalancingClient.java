/*
 *
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.client;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.Serializer;
import com.netflix.servo.monitor.Stopwatch;

/**
 * An asynchronous client that is capable of load balancing with an {@link ILoadBalancer}. It delegates the 
 * asynchronous call to the {@link AsyncClient} passed in from the constructor. As with synchronous I/O client,
 * the URI in the request can be a partial URI without host name or port. The load balancer will be responsible
 * to choose a server and calculate the final URI. If multiple retries are configured, all intermediate failures
 * will be hidden from the caller of the APIs in this class. All call results will be feed back to the load balancer
 * as server statistics to help it choosing the next server, for example, avoiding servers with consecutive connection
 * or read failures or high concurrent requests given the {@link AvailabilityFilteringRule}.
 * 
 * @author awang
 *
 * @param <T> Request type
 * @param <S> Response type
 * @param <U> Type of storage used for delivering partial content, for example, {@link ByteBuffer}
 * @param <V> Type of key to find {@link Serializer} and {@link Deserializer} for the content. For example, for HTTP communication,
 *            the key type is {@link ContentTypeBasedSerializerKey}
 */
public class AsyncLoadBalancingClient<T extends ClientRequest, S extends IResponse, U, V>
        extends LoadBalancerContext<T, S> implements AsyncClient<T, S, U, V> {
    
    private AsyncClient<T, S, U, V> asyncClient;
    private static final Logger logger = LoggerFactory.getLogger(AsyncLoadBalancingClient.class);
    
    public AsyncLoadBalancingClient(AsyncClient<T, S, U, V> asyncClient) {
        super();
        this.asyncClient = asyncClient;
    }
    
    public AsyncLoadBalancingClient(AsyncClient<T, S, U, V> asyncClient, IClientConfig clientConfig) {
        super(clientConfig);
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
            completeResponse = response;
            latch.countDown();
            if (callback != null) {
                callback.completed(response);
            }
        }

        @Override
        public void failed(Throwable e) {
            exception = e;
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

    /**
     * Execute a request with callback invoked after the full response is buffered. If multiple retries are configured,
     * all intermediate failures will be hidden from caller and only the last successful response or failure
     * will be used for callback.
     * 
     * @param request Request to execute. It can contain a partial URI without host or port as
     * the load balancer will calculate the final URI after choosing a server.
     */
    @Override
    public Future<S> execute(final T request, final BufferedResponseCallback<S> callback)
            throws ClientException {
        return execute(request, null, callback);
    }
    
    /**
     * Execute a request with callback. If multiple retries are configured,
     * all intermediate failures will be hidden from caller and only the last successful response or failure
     * will be used for callback.
     * 
     * @param request Request to execute. It can contain a partial URI without host or port as
     * the load balancer will calculate the final URI after choosing a server.
     */
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

    /**
     * Execute the same request that might be sent to multiple servers (as back up requests) if
     * no response is received within the timeout. This method delegates to 
     * {@link AsyncBackupRequestsExecutor#executeWithBackupRequests(AsyncClient, List, long, TimeUnit, StreamDecoder, ResponseCallback)} 
     *
     * @param request Request to execute. It can contain a partial URI without host or port as
     * the load balancer will calculate the final URI after choosing a server.
     * @param numServers the maximal number of servers to try before getting a response
     */
    public <E> AsyncBackupRequestsExecutor.ExecutionResult<S> executeWithBackupRequests(final T request,
            final int numServers, long timeoutIntervalBetweenRequests, TimeUnit unit,
            final StreamDecoder<E, U> decoder,
            
            final ResponseCallback<S, E> callback)
            throws ClientException {
        final List<T> requests = Lists.newArrayList();
        for (int i = 0; i < numServers; i++) {
            requests.add(computeFinalUriWithLoadBalancer(request));
        }
        return AsyncBackupRequestsExecutor.executeWithBackupRequests(this,  requests, timeoutIntervalBetweenRequests, unit, decoder, callback);
    }

    
    @Override
    public void close() throws IOException {
        if (asyncClient != null) {
            asyncClient.close();
        }
    }

    @Override
    public void addSerializationFactory(SerializationFactory<V> factory) {
        asyncClient.addSerializationFactory(factory);
    }

    /**
     * Execute a request where the future will be ready when full response is buffered. If multiple retries are configured,
     * all intermediate failures will be hidden from caller and only the last successful response or failure
     * will be used for creating the future.
     * 
     * @param request Request to execute. It can contain a partial URI without host or port as
     * the load balancer will calculate the final URI after choosing a server.
     */

    @Override
    public Future<S> execute(T request) throws ClientException {
        return execute(request, null);
    }
}
