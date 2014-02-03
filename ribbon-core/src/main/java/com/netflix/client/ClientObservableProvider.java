package com.netflix.client;

import rx.Observable;


public interface ClientObservableProvider<T, S extends ClientRequest> {
    public Observable<T> getObservableForEndpoint(S request);    
}
