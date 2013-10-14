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

public class AsyncBackupRequestsExecutor {
    public static interface ExecutionResult<T extends IResponse> extends Future<T> {
        public boolean isResponseReceived();
        public boolean isFailed();
        public Multimap<URI, Future<T>> getAllAttempts();
        public URI getExecutedURI();
    }

    public static <T extends ClientRequest, S extends IResponse, U, V> ExecutionResult<S> 
            executeWithBackupRequests(AsyncClient<T, S, U, V> asyncClient, final List<T> requests,
                final int numServers, long timeout, TimeUnit unit, final BufferedResponseCallback<S> callback) throws ClientException {
        return executeWithBackupRequests(asyncClient, requests, numServers, timeout, unit, null, callback);
    }

    public static <E, T extends ClientRequest, S extends IResponse, U, V> ExecutionResult<S> 
            executeWithBackupRequests(AsyncClient<T, S, U, V> asyncClient, final List<T> requests,
                    final int numServers, long timeout, TimeUnit unit,
                    final StreamDecoder<E, U> decoder, final ResponseCallback<S, E> callback)
            throws ClientException {
        final LinkedBlockingDeque<Future<S>> results = new LinkedBlockingDeque<Future<S>>();
        final AtomicInteger failedCount = new AtomicInteger();
        final AtomicInteger finalSequenceNumber = new AtomicInteger(-1);
        final AtomicBoolean responseRecevied = new AtomicBoolean();
        final AtomicBoolean completedCalled = new AtomicBoolean();
        final AtomicBoolean failedCalled = new AtomicBoolean();
        final AtomicBoolean cancelledCalled = new AtomicBoolean();
        final Lock lock = new ReentrantLock();
        final Condition responseChosen = lock.newCondition();
        final Multimap<URI, Future<S>> map = ArrayListMultimap.create();
        
        for (int i = 0; i < requests.size(); i++) {
            final int sequenceNumber = i;
            Future<S> future = asyncClient.execute(requests.get(i), decoder, new ResponseCallback<S, E>() {
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
                if (finalSequenceNumber.get() >= 0 || responseChosen.await(timeout, unit)) {
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
