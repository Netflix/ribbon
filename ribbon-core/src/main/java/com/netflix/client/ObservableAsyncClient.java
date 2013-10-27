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


import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.Observable.OnSubscribeFunc;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

/**
 * A class that wraps an asynchronous client and return {@link Observable} as result of execution.
 * 
 * @author awang
 *
 * @param <T> Type of the request
 * @param <S> Type of the response
 * @param <U> Type of storage used for delivering partial content, for example, {@link ByteBuffer}
 */
public class ObservableAsyncClient<T extends ClientRequest, S extends IResponse, U> implements Closeable {

    public static class StreamEvent<U extends IResponse, E> {
        private volatile U response;
        private volatile E event;

        public StreamEvent(U response, E event) {
            super();
            this.response = response;
            this.event = event;
        }
        
        public final U getResponse() {
            return response;
        }
        public final E getEvent() {
            return event;
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(ObservableAsyncClient.class);
    
    private final AsyncClient<T, S, U, ?> client;
    
    public ObservableAsyncClient(AsyncClient<T, S, U, ?> client) {
        this.client = client;
    }
    
    /**
     * Execute a request and return {@link Observable} of the fully buffered response.
     */
    public Observable<S> execute(final T request) {
        final OnSubscribeFunc<S> onSubscribeFunc = new OnSubscribeFunc<S>() {
            @Override
            public Subscription onSubscribe(final Observer<? super S> observer) {

                final CompositeSubscription parentSubscription = new CompositeSubscription();
                try {
                    parentSubscription.add(Subscriptions.from(client.execute(request, null,
                            new BufferedResponseCallback<S>() {

                                @Override
                                public void completed(S response) {
                                    observer.onNext(response);
                                    observer.onCompleted();
                                }

                                @Override
                                public void failed(Throwable e) {
                                    observer.onError(e);
                                }

                                @Override
                                public void cancelled() {
                                    observer.onError(new IllegalStateException("operation cancelled"));                                    
                                }
                    })));
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                }
                return parentSubscription;
            }
        };
        
        return Observable.create(new OnSubscribeFunc<S>() {
            @Override
            public Subscription onSubscribe(Observer<? super S> observer) {
                return onSubscribeFunc.onSubscribe(observer);
            }
        });
    }
    
    /**
     * Execute a request and return {@link Observable} of the individual entities delivered by the {@link StreamDecoder}
     * whenever some content is available in the I/O channel.
     *
     * @param <E> Type of entity delivered from {@link StreamDecoder}
     */
    public <E> Observable<StreamEvent<S, E>> stream(final T request, final StreamDecoder<E, U> decoder) {
        final OnSubscribeFunc<StreamEvent<S, E>> onSubscribeFunc = new OnSubscribeFunc<StreamEvent<S, E>>() {
            @Override
            public Subscription onSubscribe(final 
                    Observer<? super StreamEvent<S, E>> observer) {
                final CompositeSubscription parentSubscription = new CompositeSubscription();
                try {
                    Future<?> future = client.execute(request, (StreamDecoder<E, U>) decoder, 
                        new ResponseCallback<S, E>() {
                            private volatile S response;
                            @Override
                            public void completed(S response) {
                                try {
                                    observer.onCompleted();
                                } finally {
                                    // if decoder is not null, the content should have been consumed
                                    if (decoder != null) {
                                        try {
                                            response.close();
                                        } catch (IOException e) {
                                            logger.error("Error closing response", e);
                                        }
                                    }
                                }
                            }

                            @Override
                            public void failed(Throwable e) {
                                observer.onError(e);
                            }

                            @Override
                            public void cancelled() {
                                observer.onError(new IllegalStateException("operation cancelled"));
                            }

                            @Override
                            public void responseReceived(S response) {
                                this.response = response;
                            }

                            @Override
                            public void contentReceived(E content) {
                                StreamEvent<S, E> e = new StreamEvent<S, E>(this.response, content);
                                observer.onNext(e);
                            }
                    }
                    );
                    parentSubscription.add(Subscriptions.from(future));
                    
                } catch (ClientException e) {
                    logger.error("Unexpected exception", e);
                }
                return parentSubscription;
            }
            
        };
        return Observable.create(new OnSubscribeFunc<StreamEvent<S, E>>() {
            @Override
            public Subscription onSubscribe(final Observer<? super StreamEvent<S, E>> observer) {
                return onSubscribeFunc.onSubscribe(observer);
            }
        });
    }

    @Override
    public void close() {
        try {
            this.client.close();
        } catch (IOException e) {
            logger.error("Exception closing client", e);
        }
        
    }
   
}
