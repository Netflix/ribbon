package com.netflix.loadbalancer.reactive;

import com.netflix.loadbalancer.Server;
import rx.Observable;
import rx.Subscriber;
import rx.Observable.OnSubscribe;

class CommandToObservableConverter<T> implements LoadBalancerObservable<T> {
    private final LoadBalancerExecutable<T> command;
    
    static <T> LoadBalancerObservable<T> toObsevable(LoadBalancerExecutable<T> command) {
        return new CommandToObservableConverter<T>(command);
    }

    CommandToObservableConverter(LoadBalancerExecutable<T> command) {
        this.command = command;
    }
    
    @Override
    public Observable<T> run(final Server server) {
        return Observable.create(new OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> t1) {
                try {
                    T obj = command.run(server);
                    t1.onNext(obj);
                    t1.onCompleted();
                } catch (Exception e) {
                    t1.onError(e);
                }
            }
            
        });
    }
} 
