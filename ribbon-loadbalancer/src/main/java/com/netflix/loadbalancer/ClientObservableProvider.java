package com.netflix.loadbalancer;

import rx.Observable;


public interface ClientObservableProvider<T> {
    public Observable<T> getObservableForEndpoint(Server server);   
}
