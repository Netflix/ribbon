package com.netflix.client;


import java.util.concurrent.Future;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.Observable.OnSubscribeFunc;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

public class ObservableAsyncClient<T extends ClientRequest, S extends IResponse, U> {

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
    
    private final AsyncClient<T, S, U> client;
    
    public ObservableAsyncClient(AsyncClient<T, S, U> client) {
        this.client = client;
    }
    
    public Observable<S> execute(final T request) {
        final OnSubscribeFunc<S> onSubscribeFunc = new OnSubscribeFunc<S>() {
            @Override
            public Subscription onSubscribe(final Observer<? super S> observer) {

                final CompositeSubscription parentSubscription = new CompositeSubscription();
                try {
                    parentSubscription.add(Subscriptions.from(client.execute(request, null,
                            new FullResponseCallback<S>() {

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
                    // TODO Auto-generated catch block
                    e.printStackTrace();
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
    
}