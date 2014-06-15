package com.netflix.client.netty.tcp;

import java.util.concurrent.ScheduledExecutorService;

import io.netty.channel.ChannelOption;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.client.ClientBuilder;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.netty.LoadBalancingRxClientWithPoolOptions;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

public class LoadBalancingTcpClient<I, O> extends LoadBalancingRxClientWithPoolOptions<I, O, RxClient<I,O>> implements RxClient<I, O> {

    public LoadBalancingTcpClient(ILoadBalancer lb, IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<O, I> pipelineConfigurator, ScheduledExecutorService poolCleanerScheduler) {
        super(lb, config, retryHandler, pipelineConfigurator, poolCleanerScheduler);
    }

    public LoadBalancingTcpClient(IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<O, I> pipelineConfigurator,
            ScheduledExecutorService poolCleanerScheduler) {
        super(config, retryHandler, pipelineConfigurator, poolCleanerScheduler);
    }

    @Override
    protected RxClient<I, O> cacheLoadRxClient(Server server) {
        ClientBuilder<I, O> builder = RxNetty.newTcpClientBuilder(server.getHost(), server.getPort());
        if (pipelineConfigurator != null) {
            builder.pipelineConfigurator(pipelineConfigurator);
        }
        Integer connectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, null, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        builder.channelOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
        if (isPoolEnabled()) {
            builder.withConnectionPoolLimitStrategy(poolStrategy)
            .withIdleConnectionsTimeoutMillis(idleConnectionEvictionMills)
            .withPoolIdleCleanupScheduler(poolCleanerScheduler);
        } else {
            builder.withNoConnectionPooling();
        }
        RxClient<I, O> client = builder.build();
        if (isPoolEnabled()) {
            client.poolStateChangeObservable().subscribe(stats);
        }
        return client;
    }
}
