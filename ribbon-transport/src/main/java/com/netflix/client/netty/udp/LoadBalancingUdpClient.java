package com.netflix.client.netty.udp;

import rx.Subscription;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.client.ClientMetricsEvent;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.protocol.udp.client.UdpClientBuilder;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.LoadBalancingRxClient;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

public class LoadBalancingUdpClient<I, O> extends LoadBalancingRxClient<I, O, RxClient<I,O>> implements RxClient<I, O> {

    public LoadBalancingUdpClient(IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<O, I> pipelineConfigurator) {
        super(config, retryHandler, pipelineConfigurator);
    }

    
    public LoadBalancingUdpClient(ILoadBalancer lb, IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<O, I> pipelineConfigurator) {
        super(lb, config, retryHandler, pipelineConfigurator);
    }

    @Override
    protected RxClient<I, O> cacheLoadRxClient(Server server) {
        UdpClientBuilder<I, O> builder = RxNetty.newUdpClientBuilder(server.getHost(), server.getPort());
        if (pipelineConfigurator != null) {
            builder.pipelineConfigurator(pipelineConfigurator);
        }
        return builder.build();
    }


    @Override
    public Subscription subscribe(
            MetricEventsListener<? extends ClientMetricsEvent<?>> listener) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public String name() {
        // TODO Auto-generated method stub
        return null;
    }
}
