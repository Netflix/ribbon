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
package com.netflix.ribbon.transport.netty.udp;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.transport.netty.LoadBalancingRxClient;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.client.ClientMetricsEvent;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.protocol.udp.client.UdpClientBuilder;
import io.reactivex.netty.servo.udp.UdpClientListener;

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
    protected RxClient<I, O> createRxClient(Server server) {
        UdpClientBuilder<I, O> builder = RxNetty.newUdpClientBuilder(server.getHost(), server.getPort());
        if (pipelineConfigurator != null) {
            builder.pipelineConfigurator(pipelineConfigurator);
        }
        return builder.build();
    }

    @Override
    protected MetricEventsListener<? extends ClientMetricsEvent<?>> createListener(
            String name) {
        return UdpClientListener.newUdpListener(name);
    }
}
