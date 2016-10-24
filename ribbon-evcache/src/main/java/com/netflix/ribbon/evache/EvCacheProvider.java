/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.ribbon.evache;

import com.netflix.evcache.EVCache;
import com.netflix.evcache.EVCacheException;
import com.netflix.ribbon.CacheProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

/**
 * @author Tomasz Bak
 */
public class EvCacheProvider<T> implements CacheProvider<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvCacheProvider.class);

    private static final long WATCH_INTERVAL = 1;

    private static final FutureObserver FUTURE_OBSERVER;

    static {
        FUTURE_OBSERVER = new FutureObserver();
        FUTURE_OBSERVER.start();
    }

    private final EvCacheOptions options;
    private final EVCache evCache;

    public EvCacheProvider(EvCacheOptions options) {
        this.options = options;
        EVCache.Builder builder = new EVCache.Builder();
        if (options.isEnableZoneFallback()) {
            builder.enableZoneFallback();
        }
        builder.setDefaultTTL(options.getTimeToLive());
        builder.setAppName(options.getAppName());
        builder.setCacheName(options.getCacheName());
        evCache = builder.build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Observable<T> get(final String key, Map<String, Object> requestProperties) {
        return Observable.create(new OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> subscriber) {
                Future<T> getFuture;
                try {
                    if (options.getTranscoder() == null) {
                        getFuture = evCache.getAsynchronous(key);
                    } else {
                        getFuture = (Future<T>) evCache.getAsynchronous(key, options.getTranscoder());
                    }
                    FUTURE_OBSERVER.watchFuture(getFuture, subscriber);
                } catch (EVCacheException e) {
                    subscriber.onError(new CacheFaultException("EVCache exception when getting value for key " + key, e));
                }
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static final class FutureObserver extends Thread {
        private final Map<Future, Subscriber> futureMap = new ConcurrentHashMap<Future, Subscriber>();

        FutureObserver() {
            super("EvCache-Future-Observer");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (true) {
                for (Map.Entry<Future, Subscriber> f : futureMap.entrySet()) {
                    Future<?> future = f.getKey();
                    Subscriber subscriber = f.getValue();
                    if (subscriber.isUnsubscribed()) {
                        future.cancel(true);
                        futureMap.remove(future);
                    } else if (future.isDone()) {
                        try {
                            handleCompletedFuture(future, subscriber);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        } finally {
                            futureMap.remove(future);
                        }
                    }
                }

                try {
                    Thread.sleep(WATCH_INTERVAL);
                } catch (InterruptedException e) {
                    // Never terminate
                }
            }
        }

        private static void handleCompletedFuture(Future future, Subscriber subscriber) throws InterruptedException {
            if (future.isCancelled()) {
                subscriber.onError(new CacheFaultException("cache get request canceled"));
            } else {
                try {
                    Object value = future.get();
                    if (value == null) {
                        subscriber.onError(new CacheMissException());
                    } else {
                        subscriber.onNext(value);
                        subscriber.onCompleted();
                    }
                } catch (ExecutionException e) {
                    subscriber.onError(e.getCause());
                }
            }
        }

        void watchFuture(Future future, Subscriber<?> subscriber) {
            futureMap.put(future, subscriber);
        }
    }
}
