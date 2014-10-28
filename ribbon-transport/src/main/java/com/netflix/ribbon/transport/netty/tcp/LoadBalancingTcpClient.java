/*
 *
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.ribbon.transport.netty.tcp;

import java.util.concurrent.ScheduledExecutorService;

import io.netty.channel.ChannelOption;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.client.ClientBuilder;
import io.reactivex.netty.client.ClientMetricsEvent;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.servo.tcp.TcpClientListener;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.ribbon.transport.netty.LoadBalancingRxClientWithPoolOptions;
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
    protected RxClient<I, O> createRxClient(Server server) {
        ClientBuilder<I, O> builder = RxNetty.newTcpClientBuilder(server.getHost(), server.getPort());
        if (pipelineConfigurator != null) {
            builder.pipelineConfigurator(pipelineConfigurator);
        }
        Integer connectTimeout = getProperty(IClientConfigKey.Keys.ConnectTimeout, null, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        builder.channelOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
        if (isPoolEnabled()) {
            builder.withConnectionPoolLimitStrategy(poolStrategy)
            .withIdleConnectionsTimeoutMillis(idleConnectionEvictionMills)
            .withPoolIdleCleanupScheduler(poolCleanerScheduler);
        } else {
            builder.withNoConnectionPooling();
        }
        RxClient<I, O> client = builder.build();
        return client;
    }

    @Override
    protected MetricEventsListener<? extends ClientMetricsEvent<?>> createListener(
            String name) {
        return TcpClientListener.newListener(name);
    }

}
