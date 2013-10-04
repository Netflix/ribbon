package com.netflix.client;


import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.Observable.OnSubscribeFunc;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

public class ObservableAsyncClient<T extends ClientRequest, S extends ResponseWithTypedEntity> {

    private final AsyncClient<T, S> client;
    
    public ObservableAsyncClient(AsyncClient<T, S> client) {
        this.client = client;
    }
    
    public Observable<S> execute(final T request) {
        final OnSubscribeFunc<S> onSubscribeFunc = new OnSubscribeFunc<S>() {
            @Override
            public Subscription onSubscribe(final Observer<? super S> observer) {

                final CompositeSubscription parentSubscription = new CompositeSubscription();
                try {
                    parentSubscription.add(Subscriptions.from(client.execute(request, 
                            new ResponseCallback<S>() {

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
}
