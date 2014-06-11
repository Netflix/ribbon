package com.netflix.client.netty.tcp;

import rx.Observable;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.PoolStats;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.CachedRxClient;
import com.netflix.loadbalancer.ClientObservableProvider;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

public class CachedTcpClient<I, O> extends CachedRxClient<I, O, RxClient<I,O>> implements RxClient<I, O> {

    public CachedTcpClient(ILoadBalancer lb, IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<O, I> pipelineConfigurator) {
        super(lb, config, retryHandler, pipelineConfigurator);
    }

    @Override
    protected RxClient<I, O> cacheLoadRxClient(Server server) {
        return RxNetty.createTcpClient(server.getHost(), server.getPort(), pipelineConfigurator);
    }

    @Override
    public Observable<PoolStateChangeEvent> poolStateChangeObservable() {
        // TODO Auto-generated method stub
        return null;
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
        super.close();
    }

    @Override
    public PoolStats getStats() {
        // TODO Auto-generated method stub
        return null;
    }

}
