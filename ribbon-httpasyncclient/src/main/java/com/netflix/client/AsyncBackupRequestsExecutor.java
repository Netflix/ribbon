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

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

/**
 * A utility class that asynchronously execute request
 * with back up requests to reduce latency. See https://github.com/Netflix/Hystrix/issues/25
 * 
 * @author awang
 */
public class AsyncBackupRequestsExecutor {
    /**
     * Definition of the result from execution with back up requests. The added APIs
     * on top of {@link Future} do not block and may change return value depending on
     * the state and timing of the I/O process.
     * 
     * @author awang
     *
     * @param <T> Type of response, which is protocol specific.
     * 
     */
    public static interface ExecutionResult<T extends IResponse> extends Future<T> {
        /**
         * If a response has been received after sending the original request and possibly some
         * back up requests. 
         */
        public boolean isResponseReceived();
        /**
         * If the operation is failed after sending the original request and possibly some
         * back up requests.
         */
        public boolean isFailed();
        
        /**
         * Get all executed URIs and their future response, including those from back up requests.
         */
        public Multimap<URI, Future<T>> getAllAttempts();
        
        /**
         * Get the final URI of the execution where the callback is invoked upon.
         */
        public URI getExecutedURI();
    }

    /**
     * Asynchronously execute the first request in the requests list and execute subsequent requests one by one if
     * no response is received within a specified timeout period. The process stops as soon as an initial response 
     * (as opposed to the full content) is received, in which case there will be attempts to cancel 
     * pending I/O operations for all other requests. Callback passed in will be invoked on the earliest
     * response for all its life cycle methods.  If no response is received and all requests have failed, the callback will
     * be invoked for the last request sent as "failed". In any event, the callback will be invoked only once for one 
     * successful response or failed request, even if cancellation of remaining requests does not always work.
     * <p>
     * The method will block up to timeout * (size of requests list) or until the first response received, but 
     * before the full content is consumed. The callback will happen instantly and asynchronously in other threads.
     * 
     * @param asyncClient the client that is used for the asynchronous execution
     * @param requests requests to send. The first one is guaranteed to sent and the rest are the back up requests.
     * @param timeoutIntervalBetweenRequests time to wait for response between requests
     * @param unit TimeUnit for the above timeout parameter
     * @param callback callback to be invoked
     * @return The result of the execution with back up requests. See {@link ExecutionResult}.
     * @throws ClientException If any error happens when processing the request before actual I/O operation happens
     */

    public static <T extends ClientRequest, S extends IResponse, U, V> ExecutionResult<S> 
            executeWithBackupRequests(AsyncClient<T, S, U, V> asyncClient, final List<T> requests,
               long timeoutIntervalBetweenRequests, TimeUnit unit, final BufferedResponseCallback<S> callback) throws ClientException {
        return executeWithBackupRequests(asyncClient, requests, timeoutIntervalBetweenRequests, unit, null, callback);
    }

    /**
     * Asynchronously execute the first request in the requests list and execute subsequent requests one by one if
     * no response is received within a specified timeout period. The process stops as soon as an initial response 
     * (as opposed to the full content) is received, in which case there will be attempts to cancel 
     * pending I/O operations for all other requests. Callback passed in will be invoked on the earliest
     * response for all its life cycle methods.  If no response is received and all requests have failed, the callback will
     * be invoked for the last request sent as "failed". In any event, the callback will be invoked only once for one 
     * successful response or failed request, even if cancellation of remaining requests does not always work.
     * <p>
     * The method will block up to timeout * (size of requests list) or until the first response received, but 
     * before the full content is consumed. The callback will happen instantly and asynchronously in other threads.
     * 
     * @param asyncClient the client that is used for the asynchronous execution
     * @param requests requests to send. The first one is guaranteed to sent and the rest are the back up requests.
     * @param timeoutIntervalBetweenRequests time to wait for response between requests
     * @param unit TimeUnit for the above timeout parameter
     * @param decoder {@link StreamDecoder} to be used to deliver partial result if desired
     * @param callback callback to be invoked
     * @return The result of the execution with back up requests. See {@link ExecutionResult}.
     * @throws ClientException If any error happens when processing the request before actual I/O operation happens
     */
    public static <E, T extends ClientRequest, S extends IResponse, U, V> ExecutionResult<S> 
            executeWithBackupRequests(AsyncClient<T, S, U, V> asyncClient, final List<T> requests,
                    long timeoutIntervalBetweenRequests, TimeUnit unit,
                    final StreamDecoder<E, U> decoder, final ResponseCallback<S, E> callback)
            throws ClientException {
        final int numServers = requests.size();
        final LinkedBlockingDeque<Future<S>> results = new LinkedBlockingDeque<Future<S>>();
        final AtomicInteger failedCount = new AtomicInteger();
        final AtomicInteger finalSequenceNumber = new AtomicInteger(-1);
        final AtomicBoolean responseRecevied = new AtomicBoolean();
        final AtomicBoolean completedCalled = new AtomicBoolean();
        final AtomicBoolean failedCalled = new AtomicBoolean();
        // final AtomicBoolean cancelledCalled = new AtomicBoolean();
        final Lock lock = new ReentrantLock();
        final Condition responseChosen = lock.newCondition();
        final Multimap<URI, Future<S>> map = ArrayListMultimap.create();
        
        for (int i = 0; i < requests.size(); i++) {
            final int sequenceNumber = i;
            Future<S> future = asyncClient.execute(requests.get(i), decoder, new ResponseCallback<S, E>() {
                private volatile boolean chosen = false;
                private AtomicBoolean cancelledInvokedOnSameRequest = new AtomicBoolean();
                @Override
                public void completed(S response) {
                    // System.err.println("completed called");
                    // Thread.dumpStack();
                    lock.lock();
                    boolean shouldInvokeCallback = false;
                    try {
                        if (chosen) {
                            shouldInvokeCallback = true;
                            completedCalled.set(true);
                        }
                    } finally {
                        lock.unlock();
                    }
                    if (callback != null && shouldInvokeCallback) {
                        callback.completed(response);
                    }
                }

                @Override
                public void failed(Throwable e) {
                    lock.lock();
                    boolean shouldInvokeCallback = false;
                    try {
                        int count = failedCount.incrementAndGet();
                        if (count == numServers || chosen) { 
                            finalSequenceNumber.set(sequenceNumber);
                            responseChosen.signalAll();
                            shouldInvokeCallback = true;
                            failedCalled.set(true);
                        }
                    } finally {
                        lock.unlock();
                    }
                    if (shouldInvokeCallback && callback != null) {
                        callback.failed(e);
                    }
                }

                @Override
                public void cancelled() {
                    // avoid getting cancelled multiple times
                    if (cancelledInvokedOnSameRequest.compareAndSet(false, true)) {
                        lock.lock();
                        int count = failedCount.incrementAndGet();
                        boolean shouldInvokeCallback = false;
                        try {
                            if (count == numServers || chosen) {
                                // System.err.println("chosen:" + chosen);
                                // System.err.println("failed count: " + failedCount.get());
                                finalSequenceNumber.set(sequenceNumber);
                                responseChosen.signalAll();
                                shouldInvokeCallback = true;
                            }
                        } finally {
                            lock.unlock();
                        }
                        if (shouldInvokeCallback && callback != null) {
                            callback.cancelled();
                        }
                    }
                }

                @Override
                public void responseReceived(S response) {
                    if (responseRecevied.compareAndSet(false, true)) {
                        // System.err.println("chosen=true");
                        lock.lock();
                        try {
                            chosen = true;
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
                        if (finalSequenceNumber.get() >= 0 && i != finalSequenceNumber.get() && !future.isDone()) {
                            future.cancel(true);
                        }
                        i++;
                    }                    
                }
            });
            results.add(future);
            map.put(requests.get(i).getUri(), future);
            lock.lock();
            try {
                if (finalSequenceNumber.get() >= 0 || responseChosen.await(timeoutIntervalBetweenRequests, unit)) {
                    // there is a response within the specified timeout, no need to send more requests
                    break;
                }
            } catch (InterruptedException e1) {
            } finally {
                lock.unlock();
            }
        }
        return new ExecutionResult<S>() {
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

            @Override
            public boolean isResponseReceived() {
                return responseRecevied.get();
            }

            @Override
            public boolean isFailed() {
                return failedCalled.get();
            }

            @Override
            public Multimap<URI, Future<S>> getAllAttempts() {
                return map;
            }

            @Override
            public URI getExecutedURI() {
                int requestIndex = finalSequenceNumber.get(); 
                if ( requestIndex >= 0) {
                    return requests.get(requestIndex).getUri();
                } else {
                    return null;
                }
            }
        };
    }
}
