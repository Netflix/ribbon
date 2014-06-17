package com.netflix.client.netty;

import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.PoolStats;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

import rx.Observable;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.ClientObservableProvider;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.LoadBalancerExecutor;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerListChangeListener;

public abstract class LoadBalancingRxClient<I, O, T extends RxClient<I, O>> implements Closeable, RxClient<I, O> {
    
    protected final ConcurrentMap<Server, T> rxClientCache;
    protected final LoadBalancerExecutor lbExecutor;
    protected final PipelineConfigurator<O, I> pipelineConfigurator;
    protected final IClientConfig clientConfig;
    protected final RetryHandler retryHandler;

    public LoadBalancingRxClient(IClientConfig config, RetryHandler retryHandler, PipelineConfigurator<O, I> pipelineConfigurator) {
        this(LoadBalancerBuilder.newBuilder().withClientConfig(config).buildLoadBalancerFromConfigWithReflection(),
                config,
                retryHandler,
                pipelineConfigurator
                );
    }
    
    public LoadBalancingRxClient(ILoadBalancer lb, IClientConfig config, RetryHandler retryHandler, PipelineConfigurator<O, I> pipelineConfigurator) {
        rxClientCache = new ConcurrentHashMap<Server, T>();
        lbExecutor = new LoadBalancerExecutor(lb, config, retryHandler);
        this.retryHandler = retryHandler;
        this.pipelineConfigurator = pipelineConfigurator;
        this.clientConfig = config;
        addLoadBalancerListener();
    }
      
    public IClientConfig getClientConfig() {
        return clientConfig;
    }
    
    public String getName() {
        return clientConfig.getClientName();
    }
    
    public int getResponseTimeOut() {
        int maxRetryNextServer = 0;
        int maxRetrySameServer = 0;
        if (retryHandler != null) {
            maxRetryNextServer = retryHandler.getMaxRetriesOnNextServer();
            maxRetrySameServer = retryHandler.getMaxRetriesOnSameServer();
        } else {
            maxRetryNextServer = clientConfig.getPropertyWithType(IClientConfigKey.CommonKeys.MaxAutoRetriesNextServer, DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER);
            maxRetrySameServer = clientConfig.getPropertyWithType(IClientConfigKey.CommonKeys.MaxAutoRetries, DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES);
        }
        int readTimeout = getProperty(IClientConfigKey.CommonKeys.ReadTimeout, null, DefaultClientConfigImpl.DEFAULT_READ_TIMEOUT);
        int connectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, null, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        return (maxRetryNextServer + 1) * (maxRetrySameServer + 1) * (readTimeout + connectTimeout);
    }
    
    public int getMaxConcurrentRequests() {
        return -1;
    }
        
    protected <S> S getProperty(IClientConfigKey<S> key, @Nullable IClientConfig requestConfig, S defaultValue) {
        if (requestConfig != null && requestConfig.getPropertyWithType(key) != null) {
            return requestConfig.getPropertyWithType(key);
        } else {
            return clientConfig.getPropertyWithType(key, defaultValue);
        }
    }

    /**
     * This is where we remove HttpClient and shutdown its connection pool if it is no longer available from load balancer.
     */
    private void addLoadBalancerListener() {
        ILoadBalancer lb = lbExecutor.getLoadBalancer();
        if (!(lb instanceof DynamicServerListLoadBalancer)) {
            return;
        }
        ((DynamicServerListLoadBalancer<?>) lb).addServerListChangeListener(new ServerListChangeListener() {
            @Override
            public void serverListChanged(List<Server> oldList, List<Server> newList) {
                for (Server server: rxClientCache.keySet()) {
                    if (!newList.contains(server)) {
                        // this server is no longer in UP status
                        removeClient(server);
                    }
                }
            }
        });
    }

    protected abstract T cacheLoadRxClient(Server server);
    
    protected T getRxClient(String host, int port) {
        Server server = new Server(host, port);
        T client =  rxClientCache.get(server);
        if (client != null) {
            return client;
        } else {
            client = cacheLoadRxClient(server);
            T old = rxClientCache.putIfAbsent(server, client);
            if (old != null) {
                return old;
            } else {
                return client;
            }
        }
    }
    
    protected T removeClient(Server server) {
        T client = rxClientCache.remove(server);
        client.shutdown();
        return client;
    }

    @Override
    public void close() {
        for (Server server: rxClientCache.keySet()) {
            removeClient(server);
        }
    }
    
    @Override
    public Observable<ObservableConnection<O, I>> connect() {
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<ObservableConnection<O, I>>() {
            @Override
            public Observable<ObservableConnection<O, I>> getObservableForEndpoint(
                    Server server) {
                return getRxClient(server.getHost(), server.getPort()).connect();
            }
        });
    }

    @Override
    public void shutdown() {
        close();
    }

    @Override
    public Observable<PoolStateChangeEvent> poolStateChangeObservable() {
        return Observable.empty();
    }

    @Override
    public PoolStats getStats() {
        return null;
    }
}
