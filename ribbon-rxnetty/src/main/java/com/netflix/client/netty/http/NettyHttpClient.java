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

package com.netflix.client.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.reactivex.netty.client.CompositePoolLimitDeterminationStrategy;
import io.reactivex.netty.client.MaxConnectionsBasedStrategy;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClient.HttpClientConfig;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import rx.Observable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.config.IClientConfigKey.CommonKeys;
import com.netflix.client.netty.DynamicPropertyBasedPoolStrategy;
import com.netflix.config.DynamicIntProperty;
import com.netflix.loadbalancer.Server;
import com.netflix.utils.ScheduledThreadPoolExectuorWithDynamicSize;

/**
 * A Netty HttpClient that can connect to different servers. Internally it caches the RxNetty's HttpClient, with each created with 
 * a connection pool governed by {@link CompositePoolLimitDeterminationStrategy} that has a global limit and per server limit. 
 *   
 * @author awang
 */
public class NettyHttpClient<I, O> extends AbstractNettyHttpClient<I, O> implements Closeable {

    protected static final PipelineConfigurator<HttpClientResponse<ByteBuf>, HttpClientRequest<ByteBuf>> DEFAULT_PIPELINE_CONFIGURATOR = 
            PipelineConfigurators.httpClientConfigurator();
    protected final CompositePoolLimitDeterminationStrategy poolStrategy;
    protected final MaxConnectionsBasedStrategy globalStrategy;
    protected final int idleConnectionEvictionMills;
    protected final GlobalPoolStats stats;
    protected final PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipeLineConfigurator;
    protected ConcurrentHashMap<Server, HttpClient<I, O>> rxClientCache;

    private static final ScheduledExecutorService poolCleanerScheduler;
    private static final DynamicIntProperty POOL_CLEANER_CORE_SIZE = new DynamicIntProperty("rxNetty.poolCleaner.coreSize", 2);
    
    static {
        ThreadFactory factory = (new ThreadFactoryBuilder()).setDaemon(true)
                .setNameFormat("RxClient_Connection_Pool_Clean_Up")
                .build();
        poolCleanerScheduler = new ScheduledThreadPoolExectuorWithDynamicSize(POOL_CLEANER_CORE_SIZE, factory);
    }
    
    public static NettyHttpClient<ByteBuf, ByteBuf> createDefaultHttpClient(IClientConfig config) {
        return new NettyHttpClient<ByteBuf, ByteBuf>(config, DEFAULT_PIPELINE_CONFIGURATOR);
    }

    public static NettyHttpClient<ByteBuf, ByteBuf> createDefaultHttpClient() {
        return new NettyHttpClient<ByteBuf, ByteBuf>(DefaultClientConfigImpl.getClientConfigWithDefaultValues(), DEFAULT_PIPELINE_CONFIGURATOR);
    }

    public NettyHttpClient(PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipeLineConfigurator) {
        this(DefaultClientConfigImpl.getClientConfigWithDefaultValues(), pipeLineConfigurator);
    }
    
    public NettyHttpClient(IClientConfig config, PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipeLineConfigurator) {
        super(config);
        Preconditions.checkNotNull(pipeLineConfigurator);
        this.pipeLineConfigurator = pipeLineConfigurator;
        int maxTotalConnections = config.getPropertyWithType(IClientConfigKey.CommonKeys.MaxTotalHttpConnections,
                DefaultClientConfigImpl.DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS);
        int maxConnections = config.getPropertyWithType(CommonKeys.MaxHttpConnectionsPerHost, DefaultClientConfigImpl.DEFAULT_MAX_HTTP_CONNECTIONS_PER_HOST);
        MaxConnectionsBasedStrategy perHostStrategy = new DynamicPropertyBasedPoolStrategy(maxConnections,
                config.getClientName() + "." + config.getNameSpace() + "." + CommonClientConfigKey.MaxHttpConnectionsPerHost);
        globalStrategy = new DynamicPropertyBasedPoolStrategy(maxTotalConnections, 
                config.getClientName() + "." + config.getNameSpace() + "." + CommonClientConfigKey.MaxTotalHttpConnections);
        poolStrategy = new CompositePoolLimitDeterminationStrategy(perHostStrategy, globalStrategy);
        idleConnectionEvictionMills = config.getPropertyWithType(CommonKeys.ConnIdleEvictTimeMilliSeconds, DefaultClientConfigImpl.DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS);
        rxClientCache = new ConcurrentHashMap<Server, HttpClient<I,O>>();
        stats = new GlobalPoolStats(config.getClientName(), this);
    }
    
    protected HttpClient<I, O> cacheLoadRxClient(Server server) {
        HttpClientBuilder<I, O> clientBuilder =
                new HttpClientBuilder<I, O>(server.getHost(), server.getPort()).pipelineConfigurator(pipeLineConfigurator);
        Integer connectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, null, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        Integer readTimeout = getProperty(IClientConfigKey.CommonKeys.ReadTimeout, null, DefaultClientConfigImpl.DEFAULT_READ_TIMEOUT);
        Boolean followRedirect = getProperty(IClientConfigKey.CommonKeys.FollowRedirects, null, null);
        HttpClientConfig.Builder builder = new HttpClientConfig.Builder().readTimeout(readTimeout, TimeUnit.MILLISECONDS);
        if (followRedirect != null) {
            builder.setFollowRedirect(followRedirect);
        }
        RxClient.ClientConfig rxClientConfig = builder.build();
        HttpClient<I, O> client = clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .config(rxClientConfig)
                .withConnectionPoolLimitStrategy(poolStrategy)
                .withIdleConnectionsTimeoutMillis(idleConnectionEvictionMills)
                .withPoolIdleCleanupScheduler(poolCleanerScheduler)
                .build();
        client.poolStateChangeObservable().subscribe(stats);
        return client;
    }

    @Override
    public Observable<HttpClientResponse<O>> submit(String host,
            int port, HttpClientRequest<I> request) {
        return super.submit(host, port, request);
    }

    @Override
    public Observable<HttpClientResponse<O>> submit(String host, int port,
            HttpClientRequest<I> request, IClientConfig requestConfig) {
        return super.submit(host, port, request, requestConfig);
    }
    
    public int getMaxTotalConnections() {
        return globalStrategy.getMaxConnections();
    }
    
    @VisibleForTesting
    GlobalPoolStats getGlobalPoolStats() {
        return stats;
    }

    @Override
    protected HttpClient<I, O> getRxClient(String host, int port) {
        Server server = new Server(host, port);
        HttpClient<I, O> client =  rxClientCache.get(server);
        if (client != null) {
            return client;
        } else {
            client = cacheLoadRxClient(server);
            HttpClient<I, O> old = rxClientCache.putIfAbsent(server, client);
            if (old != null) {
                return old;
            } else {
                return client;
            }
        }
    }
    
    protected ConcurrentMap<Server, HttpClient<I, O>> getCurrentHttpClients() {
        return rxClientCache;
    }
    
    protected HttpClient<I, O> removeClient(Server server) {
        HttpClient<I, O> client = rxClientCache.remove(server);
        client.shutdown();
        return client;
    }

    public void close() throws IOException {
        for (Server server: rxClientCache.keySet()) {
            removeClient(server);
        }
    }
}
