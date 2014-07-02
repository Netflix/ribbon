package com.netflix.loadbalancer;

import rx.Observable;


public interface LoadBalancerObservableCommand<T> {
    /**
     * @return The {@link Observable} for the server. It is expected
     * that the actual execution is not started until the returned {@link Observable} is subscribed to.
     */
    public Observable<T> run(Server server);   
}
