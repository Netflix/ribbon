package com.netflix.loadbalancer;

import rx.Observable;
import rx.Subscriber;
import rx.Observable.OnSubscribe;

public class CommandToObservableConverter<T> implements LoadBalancerObservableCommand<T> {
    private final LoadBalancerCommand<T> command;
    
    public static <T> LoadBalancerObservableCommand<T> toObsevableCommand(LoadBalancerCommand<T> command) {
        return new CommandToObservableConverter<T>(command);
    }

    public CommandToObservableConverter(LoadBalancerCommand<T> command) {
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
