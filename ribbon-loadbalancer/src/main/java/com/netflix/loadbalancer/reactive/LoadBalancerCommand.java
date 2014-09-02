package com.netflix.loadbalancer.reactive;

import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import rx.Observable;

import java.net.URI;

/**
 * @author Allen Wang
 */
public abstract class LoadBalancerCommand<T> implements LoadBalancerExecutable<T> {

    private final LoadBalancerObservableCommand<T> observableCommand;
    private final URI loadBalancerURI;
    private final Object loadBalancerKey;
    private final LoadBalancerContext loadBalancerContext;
    private final RetryHandler retryHandler;


    public LoadBalancerCommand(LoadBalancerContext loadBalancerContext) {
        this(loadBalancerContext, null, null, null);
    }

    public LoadBalancerCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler) {
        this(loadBalancerContext, retryHandler, null, null);
    }

    public LoadBalancerCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler, URI loadBalancerURI, Object loadBalancerKey) {
        this.loadBalancerContext = loadBalancerContext;
        this.retryHandler = retryHandler;
        this.loadBalancerURI = loadBalancerURI;
        this.loadBalancerKey = loadBalancerKey;
        this.observableCommand = createObservableCommand();
    }

    private LoadBalancerObservableCommand<T> createObservableCommand() {
        return new LoadBalancerObservableCommand<T>(loadBalancerContext, retryHandler, loadBalancerURI, loadBalancerKey, null) {
            @Override
            public Observable<T> run(final Server server) {
                return CommandToObservableConverter.toObsevable(LoadBalancerCommand.this).run(server);
            }
        };
    }

    public T execute() throws Exception {
        return RxUtils.getSingleValueWithRealErrorCause(observableCommand.toObservable());
    }
}
