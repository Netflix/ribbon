package com.netflix.client;

import rx.Observable;
import com.netflix.loadbalancer.Server;


public interface ClientObservableProvider<T> {
    public Observable<T> getObservableForEndpoint(Server server);   
}
