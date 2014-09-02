package com.netflix.loadbalancer.reactive;

import com.netflix.loadbalancer.Server;
import rx.Observable;


public interface LoadBalancerObservable<T> {
    /**
     * @return The {@link Observable} for the server. It is expected
     * that the actual execution is not started until the returned {@link Observable} is subscribed to.
     */
    public Observable<T> run(Server server);
}
