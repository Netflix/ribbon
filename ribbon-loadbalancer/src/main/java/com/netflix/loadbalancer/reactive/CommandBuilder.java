package com.netflix.loadbalancer.reactive;

import com.netflix.client.ExecutionContext;
import com.netflix.client.ExecutionContextListenerInvoker;
import com.netflix.client.ExecutionListener;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import rx.Observable;

import java.net.URI;
import java.util.List;

/**
 * @author Allen Wang
 */
public class CommandBuilder<T> {

    private RetryHandler retryHandler;
    private ILoadBalancer loadBalancer;
    private IClientConfig config;
    private LoadBalancerContext loadBalancerContext;
    private List<? extends ExecutionListener<?, T>> listeners;
    private Object loadBalancerKey;
    private URI serviceLocator;

    private CommandBuilder() {}


    public static <T> CommandBuilder<T> newBuilder() {
        return new CommandBuilder<T>();
    }

    public CommandBuilder<T> withLoadBalancer(ILoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        return this;
    }

    public CommandBuilder<T> withListeners(List<? extends ExecutionListener<?, T>> listeners) {
        this.listeners = listeners;
        return this;
    }

    public CommandBuilder<T> withRetryHandler(RetryHandler retryHandler) {
        this.retryHandler = retryHandler;
        return this;
    }

    public CommandBuilder<T> withClientConfig(IClientConfig config) {
        this.config = config;
        return this;
    }

    public CommandBuilder<T> withServiceLocator(URI serviceLocator) {
        this.serviceLocator = serviceLocator;
        return this;
    }

    public CommandBuilder<T> withServerLocator(Object key) {
        this.loadBalancerKey = key;
        return this;
    }

    public CommandBuilder<T> withLoadBalancerContext(LoadBalancerContext loadBalancerContext) {
        this.loadBalancerContext = loadBalancerContext;
        return this;
    }

    public LoadBalancerRetrySameServerCommand<T> build() {
        return build((ExecutionContext<?>) null);
    }

    public LoadBalancerRetrySameServerCommand<T> build(ExecutionContext<?> executionContext) {
        if (loadBalancerContext == null && loadBalancer == null) {
            throw new IllegalArgumentException("Either LoadBalancer or LoadBalancerContext needs to be set");
        }
        ExecutionContextListenerInvoker invoker = null;

        if (listeners != null && listeners.size() > 0 && executionContext != null) {
            invoker = new ExecutionContextListenerInvoker(executionContext, listeners);
        }
        LoadBalancerContext loadBalancerContext1 = loadBalancerContext == null ? new LoadBalancerContext(loadBalancer, config) : loadBalancerContext;
        return new LoadBalancerRetrySameServerCommand<T>(loadBalancerContext1, retryHandler, invoker);
    }

    public LoadBalancerObservableCommand<T> build(final LoadBalancerObservable<T> executable) {
        return build(executable, null);
    }

    public LoadBalancerObservableCommand<T> build(final LoadBalancerObservable<T> executable, ExecutionContext<?> executionContext) {
        if (loadBalancerContext == null && loadBalancer == null) {
            throw new IllegalArgumentException("Either LoadBalancer or LoadBalancerContext needs to be set");
        }
        ExecutionContextListenerInvoker invoker = null;

        if (listeners != null && listeners.size() > 0) {
            invoker = new ExecutionContextListenerInvoker(executionContext, listeners);
        }
        LoadBalancerContext loadBalancerContext1 = loadBalancerContext == null ? new LoadBalancerContext(loadBalancer, config) : loadBalancerContext;
        return new LoadBalancerObservableCommand<T>(loadBalancerContext1, retryHandler, serviceLocator, loadBalancerKey, invoker) {
            @Override
            public Observable<T> run(Server server) {
                return executable.run(server);
            }
        };
    }

    public LoadBalancerCommand<T> build(final LoadBalancerExecutable<T> executable) {
        if (loadBalancerContext == null && loadBalancer == null) {
            throw new IllegalArgumentException("Either LoadBalancer or LoadBalancerContext needs to be set");
        }
        LoadBalancerContext loadBalancerContext1 = loadBalancerContext == null ? new LoadBalancerContext(loadBalancer, config) : loadBalancerContext;
        return new LoadBalancerCommand<T>(loadBalancerContext1, retryHandler, serviceLocator, loadBalancerKey) {
            @Override
            public T run(Server server) throws Exception {
                return executable.run(server);
            }
        };
    }
}
