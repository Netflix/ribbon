package com.netflix.loadbalancer;

import com.netflix.client.RetryHandler;
import com.netflix.utils.RxUtils;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

import java.net.URI;

/**
 * @author Allen Wang
 */
public abstract class LoadBalancerRunnableCommand<T> implements LoadBalancerCommand<T> {

    private final LoadBalancerCommand2<T> observableCommand;
    private final URI loadBalancerURI;
    private final Object loadBalancerKey;
    private final LoadBalancerContext loadBalancerContext;
    private final RetryHandler retryHandler;


    public LoadBalancerRunnableCommand(LoadBalancerContext loadBalancerContext) {
        this(loadBalancerContext, null, null, null);
    }

    public LoadBalancerRunnableCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler) {
        this(loadBalancerContext, retryHandler, null, null);
    }

    public LoadBalancerRunnableCommand(LoadBalancerContext loadBalancerContext, RetryHandler retryHandler, URI loadBalancerURI, Object loadBalancerKey) {
        this.loadBalancerContext = loadBalancerContext;
        this.retryHandler = retryHandler;
        this.loadBalancerURI = loadBalancerURI;
        this.loadBalancerKey = loadBalancerKey;
        this.observableCommand = createObservableCommand();
    }

    private LoadBalancerCommand2<T> createObservableCommand() {
        return new LoadBalancerCommand2<T>(loadBalancerContext, retryHandler, loadBalancerURI, loadBalancerKey) {
            @Override
            public Observable<T> run(final Server server) {
                return Observable.create(new OnSubscribe<T>() {
                    @Override
                    public void call(Subscriber<? super T> t1) {
                        try {
                            T obj = LoadBalancerRunnableCommand.this.run(server);
                            t1.onNext(obj);
                            t1.onCompleted();
                        } catch (Exception e) {
                            t1.onError(e);
                        }
                    }

                });
            }
        };
    }

    public T execute() throws Exception {
        return RxUtils.getSingleValueWithRealErrorCause(observableCommand.create());
    }
}
