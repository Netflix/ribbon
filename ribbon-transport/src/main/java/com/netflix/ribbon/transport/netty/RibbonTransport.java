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
package com.netflix.ribbon.transport.netty;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.loadbalancer.reactive.ExecutionListener;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.transport.netty.http.LoadBalancingHttpClient;
import com.netflix.ribbon.transport.netty.http.NettyHttpLoadBalancerErrorHandler;
import com.netflix.ribbon.transport.netty.http.SSEClient;
import com.netflix.ribbon.transport.netty.tcp.LoadBalancingTcpClient;
import com.netflix.ribbon.transport.netty.udp.LoadBalancingUdpClient;
import com.netflix.config.DynamicIntProperty;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.utils.ScheduledThreadPoolExectuorWithDynamicSize;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramPacket;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public final class RibbonTransport {
    
    public static final PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<ByteBuf>> DEFAULT_SSE_PIPELINE_CONFIGURATOR  = 
            PipelineConfigurators.sseClientConfigurator();

    public static final PipelineConfigurator<HttpClientResponse<ByteBuf>, HttpClientRequest<ByteBuf>> DEFAULT_HTTP_PIPELINE_CONFIGURATOR = 
            PipelineConfigurators.httpClientConfigurator();
    
    public static final DynamicIntProperty POOL_CLEANER_CORE_SIZE = new DynamicIntProperty("rxNetty.poolCleaner.coreSize", 2);

    public static final ScheduledExecutorService poolCleanerScheduler;

    static {
        ThreadFactory factory = (new ThreadFactoryBuilder()).setDaemon(true)
                .setNameFormat("RxClient_Connection_Pool_Clean_Up")
                .build();
        poolCleanerScheduler = new ScheduledThreadPoolExectuorWithDynamicSize(POOL_CLEANER_CORE_SIZE, factory);
    }

    private RibbonTransport() {
    }
 
    private static RetryHandler getDefaultHttpRetryHandlerWithConfig(IClientConfig config) {
        return new NettyHttpLoadBalancerErrorHandler(config);
    }
    
    private static RetryHandler getDefaultRetryHandlerWithConfig(IClientConfig config) {
        return new DefaultLoadBalancerRetryHandler(config);
    }
    
    public static RxClient<ByteBuf, ByteBuf> newTcpClient(ILoadBalancer loadBalancer, IClientConfig config) {
        return new LoadBalancingTcpClient<ByteBuf, ByteBuf>(loadBalancer, config, getDefaultRetryHandlerWithConfig(config), null, poolCleanerScheduler);
    }
    
    public static <I, O> RxClient<I, O> newTcpClient(ILoadBalancer loadBalancer, PipelineConfigurator<O, I> pipelineConfigurator, 
            IClientConfig config, RetryHandler retryHandler) {
        return new LoadBalancingTcpClient<I, O>(loadBalancer, config, retryHandler, pipelineConfigurator, poolCleanerScheduler);
    }
    
    public static <I, O> RxClient<I, O> newTcpClient(PipelineConfigurator<O, I> pipelineConfigurator, 
            IClientConfig config) {
        return new LoadBalancingTcpClient<I, O>(config, getDefaultRetryHandlerWithConfig(config), pipelineConfigurator, poolCleanerScheduler);    
    }

    public static RxClient<ByteBuf, ByteBuf> newTcpClient(IClientConfig config) {
        return new LoadBalancingTcpClient<ByteBuf, ByteBuf>(config, getDefaultRetryHandlerWithConfig(config), null, poolCleanerScheduler);    
    }
 
    public static RxClient<DatagramPacket, DatagramPacket> newUdpClient(ILoadBalancer loadBalancer, IClientConfig config) {
        return new LoadBalancingUdpClient<DatagramPacket, DatagramPacket>(loadBalancer, config, getDefaultRetryHandlerWithConfig(config), null);
    }
 
    public static RxClient<DatagramPacket, DatagramPacket> newUdpClient(IClientConfig config) {
        return new LoadBalancingUdpClient<DatagramPacket, DatagramPacket>(config, getDefaultRetryHandlerWithConfig(config), null);
    }
    
    public static <I, O> RxClient<I, O> newUdpClient(ILoadBalancer loadBalancer, PipelineConfigurator<O, I> pipelineConfigurator, 
            IClientConfig config, RetryHandler retryHandler) {
        return new LoadBalancingUdpClient<I, O>(loadBalancer, config, retryHandler, pipelineConfigurator);
    }
    
    public static <I, O> RxClient<I, O> newUdpClient(PipelineConfigurator<O, I> pipelineConfigurator, IClientConfig config) {
        return new LoadBalancingUdpClient<I, O>(config, getDefaultRetryHandlerWithConfig(config), pipelineConfigurator);
    }

    public static LoadBalancingHttpClient<ByteBuf, ByteBuf> newHttpClient() {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
        return newHttpClient(config);
    }
     
    public static LoadBalancingHttpClient<ByteBuf, ByteBuf> newHttpClient(ILoadBalancer loadBalancer, IClientConfig config) {
        return LoadBalancingHttpClient.<ByteBuf, ByteBuf>builder()
                .withLoadBalancer(loadBalancer)
                .withClientConfig(config)
                .withRetryHandler(getDefaultHttpRetryHandlerWithConfig(config))
                .withPipelineConfigurator(DEFAULT_HTTP_PIPELINE_CONFIGURATOR)
                .withPoolCleanerScheduler(poolCleanerScheduler)
                .build();
    }
    
    public static LoadBalancingHttpClient<ByteBuf, ByteBuf> newHttpClient(ILoadBalancer loadBalancer, IClientConfig config, RetryHandler retryHandler) {
        return LoadBalancingHttpClient.<ByteBuf, ByteBuf>builder()
                .withLoadBalancer(loadBalancer)
                .withClientConfig(config)
                .withRetryHandler(retryHandler)
                .withPipelineConfigurator(DEFAULT_HTTP_PIPELINE_CONFIGURATOR)
                .withPoolCleanerScheduler(poolCleanerScheduler)
                .build();
    }

    public static LoadBalancingHttpClient<ByteBuf, ByteBuf> newHttpClient(ILoadBalancer loadBalancer, IClientConfig config, RetryHandler retryHandler,
                                                                  List<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>> listeners) {
        return LoadBalancingHttpClient.<ByteBuf, ByteBuf>builder()
                .withLoadBalancer(loadBalancer)
                .withClientConfig(config)
                .withRetryHandler(retryHandler)
                .withPipelineConfigurator(DEFAULT_HTTP_PIPELINE_CONFIGURATOR)
                .withPoolCleanerScheduler(poolCleanerScheduler)
                .withExecutorListeners(listeners)
                .build();
    }


    public static LoadBalancingHttpClient<ByteBuf, ByteBuf> newHttpClient(IClientConfig config) {
        return LoadBalancingHttpClient.<ByteBuf, ByteBuf>builder()
                .withClientConfig(config)
                .withRetryHandler(getDefaultHttpRetryHandlerWithConfig(config))
                .withPipelineConfigurator(DEFAULT_HTTP_PIPELINE_CONFIGURATOR)
                .withPoolCleanerScheduler(poolCleanerScheduler)
                .build();
    }
    
    public static LoadBalancingHttpClient<ByteBuf, ByteBuf> newHttpClient(ILoadBalancer loadBalancer) {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
        return newHttpClient(loadBalancer, config);
    }

    
    public static <I, O> LoadBalancingHttpClient<I, O> newHttpClient(PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
            ILoadBalancer loadBalancer, IClientConfig config) {
        return LoadBalancingHttpClient.<I, O>builder()
                .withLoadBalancer(loadBalancer)
                .withClientConfig(config)
                .withRetryHandler(getDefaultHttpRetryHandlerWithConfig(config))
                .withPipelineConfigurator(pipelineConfigurator)
                .withPoolCleanerScheduler(poolCleanerScheduler)
                .build();
    }
    
    public static <I, O> LoadBalancingHttpClient<I, O> newHttpClient(PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
            IClientConfig config) {
        return LoadBalancingHttpClient.<I, O>builder()
                .withClientConfig(config)
                .withRetryHandler(getDefaultHttpRetryHandlerWithConfig(config))
                .withPipelineConfigurator(pipelineConfigurator)
                .withPoolCleanerScheduler(poolCleanerScheduler)
                .build();
    }
    
    public static <I, O> LoadBalancingHttpClient<I, O> newHttpClient(PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
            IClientConfig config, RetryHandler retryHandler) {
        return LoadBalancingHttpClient.<I, O>builder()
                .withClientConfig(config)
                .withRetryHandler(retryHandler)
                .withPipelineConfigurator(pipelineConfigurator)
                .withPoolCleanerScheduler(poolCleanerScheduler)
                .build();
    }

    public static <I, O> LoadBalancingHttpClient<I, O> newHttpClient(PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
                                                             ILoadBalancer loadBalancer, IClientConfig config, RetryHandler retryHandler,
                                                                  List<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>> listeners) {
        return LoadBalancingHttpClient.<I, O>builder()
                .withLoadBalancer(loadBalancer)
                .withClientConfig(config)
                .withRetryHandler(retryHandler)
                .withPipelineConfigurator(pipelineConfigurator)
                .withPoolCleanerScheduler(poolCleanerScheduler)
                .withExecutorListeners(listeners)
                .build();
    }

    public static LoadBalancingHttpClient<ByteBuf, ServerSentEvent> newSSEClient(ILoadBalancer loadBalancer, IClientConfig config) {
        return SSEClient.<ByteBuf>sseClientBuilder()
                .withLoadBalancer(loadBalancer)
                .withClientConfig(config)
                .withRetryHandler(getDefaultHttpRetryHandlerWithConfig(config))
                .withPipelineConfigurator(DEFAULT_SSE_PIPELINE_CONFIGURATOR)
                .build();
    }
 
    public static LoadBalancingHttpClient<ByteBuf, ServerSentEvent> newSSEClient(IClientConfig config) {
        return SSEClient.<ByteBuf>sseClientBuilder()
                .withClientConfig(config)
                .withRetryHandler(getDefaultHttpRetryHandlerWithConfig(config))
                .withPipelineConfigurator(DEFAULT_SSE_PIPELINE_CONFIGURATOR)
                .build();
    }
    
    public static <I> LoadBalancingHttpClient<I, ServerSentEvent> newSSEClient(PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipelineConfigurator,
            ILoadBalancer loadBalancer, IClientConfig config) {
        return SSEClient.<I>sseClientBuilder()
                .withLoadBalancer(loadBalancer)
                .withClientConfig(config)
                .withRetryHandler(getDefaultHttpRetryHandlerWithConfig(config))
                .withPipelineConfigurator(pipelineConfigurator)
                .build();
    }
    
    public static <I> LoadBalancingHttpClient<I, ServerSentEvent> newSSEClient(PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipelineConfigurator,
            IClientConfig config) {
        return SSEClient.<I>sseClientBuilder()
                .withClientConfig(config)
                .withRetryHandler(getDefaultHttpRetryHandlerWithConfig(config))
                .withPipelineConfigurator(pipelineConfigurator)
                .build();
    }

    public static LoadBalancingHttpClient<ByteBuf, ServerSentEvent> newSSEClient() {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
        return newSSEClient(config);
    }
}

