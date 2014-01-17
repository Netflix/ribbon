package com.netflix.client;

import rx.Observable;


public interface LoadBalancerObservableRequest<T, S extends ClientRequest> {
    public Observable<T> getSingleServerObservable(S request);    
}
