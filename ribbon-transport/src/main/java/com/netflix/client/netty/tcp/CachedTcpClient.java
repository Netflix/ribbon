package com.netflix.client.netty.tcp;

import io.reactivex.netty.RxNetty;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.CachedRxClient;
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
}
