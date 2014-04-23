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
import io.reactivex.netty.client.PoolStats;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClient.HttpClientConfig;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Observer;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.config.IClientConfigKey.CommonKeys;
import com.netflix.config.DynamicIntProperty;
import com.netflix.loadbalancer.Server;
import com.netflix.utils.ScheduledThreadPoolExectuorWithDynamicSize;

/**
 * An HTTP client built on top of Netty and RxJava. The core APIs are
 * 
 *  <li>{@link #createEntityObservable(HttpClientRequest, TypeDef, IClientConfig)}
 *  <li>{@link #createFullHttpResponseObservable(HttpClientRequest, IClientConfig)}
 *  <li>{@link #createServerSentEventEntityObservable(HttpClientRequest, TypeDef, IClientConfig)}
 *  <li>{@link #createServerSentEventObservable(HttpClientRequest, IClientConfig)}
 *  <li>{@link #createObservableHttpResponse(HttpClientRequest, PipelineConfigurator, IClientConfig, io.reactivex.netty.client.RxClient.ClientConfig)}
 *  <br/><br/>
 *  <p/>
 *  These APIs return an {@link Observable}, but does not start execution of the request. Once an {@link Observer} is subscribed to the returned
 *  {@link Observable}, the execution will start asynchronously. Each subscription to the {@link Observable} will result in a new execution 
 *  of the request. Unsubscribing from the Observable will cancel the request. 
 *  Please consult <a href="http://netflix.github.io/RxJava/javadoc/rx/Observable.html">RxJava API</a> for details.  
 *  <br/><br/>
 *  The APIs starting with prefix "observe" are provided on top of the core APIs to allow the execution to start immediately and 
 *  asynchronously with a passed in {@link Observer}. This is basically the "callback" pattern.
 *  <br/><br/>
 *  The "execute" APIs are provided on top of the core APIs to offer synchronous call semantics. 
 *  <br/><br/>
 *  To support serialization and deserialization of entities in full Http response or Sever-Sent-Event stream, 
 *  a {@link SerializationFactory} which provides Jackson codec is installed
 *  by default. You can override the default serialization by passing in an {@link IClientConfig} with a {@link CommonClientConfigKey#Deserializer}
 *  and/or {@link CommonClientConfigKey#Serializer} property. You also need to pass in {@link TypeDef} object
 *  to hold the reference of the runtime entity type to overcome type erasure.
 *  <br/><br/>
 *  You may find {@link NettyHttpClientBuilder} is easier to create instances of {@link NettyHttpClient}.
 *  
 * @author awang
 *
 */
public class NettyHttpClient<I, O> extends CachedNettyHttpClient<I, O> {

    protected static final PipelineConfigurator<HttpClientResponse<ByteBuf>, HttpClientRequest<ByteBuf>> DEFAULT_PIPELINE_CONFIGURATOR = 
            PipelineConfigurators.httpClientConfigurator();
    protected final CompositePoolLimitDeterminationStrategy poolStrategy;
    protected final MaxConnectionsBasedStrategy globalStrategy;
    protected final int idleConnectionEvictionMills;
    protected final GlobalPoolStats stats;
    protected final PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipeLineConfigurator;
    
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
        MaxConnectionsBasedStrategy perHostStrategy = new MaxConnectionsBasedStrategy(maxConnections);
        globalStrategy = new MaxConnectionsBasedStrategy(maxTotalConnections);
        poolStrategy = new CompositePoolLimitDeterminationStrategy(perHostStrategy, globalStrategy);
        idleConnectionEvictionMills = config.getPropertyWithType(CommonKeys.ConnIdleEvictTimeMilliSeconds, DefaultClientConfigImpl.DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS);
        stats = new GlobalPoolStats(config.getClientName(), this);
    }
    
    @Override
    protected HttpClient<I, O> createRxClient(Server server) {
        HttpClientBuilder<I, O> clientBuilder =
                new HttpClientBuilder<I, O>(server.getHost(), server.getPort()).pipelineConfigurator(pipeLineConfigurator);
        int requestConnectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, null);
        int requestReadTimeout = getProperty(IClientConfigKey.CommonKeys.ReadTimeout, null);
        HttpClientConfig.Builder builder = new HttpClientConfig.Builder().readTimeout(requestReadTimeout, TimeUnit.MILLISECONDS);
        RxClient.ClientConfig rxClientConfig = builder.build();
        
        HttpClient<I, O> client = clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, requestConnectTimeout)
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
    
    protected void setMaxTotalConnections(int newMax) {
        globalStrategy.incrementMaxConnections(newMax - getMaxTotalConnections());
    }
    
    public int getMaxTotalConnections() {
        return globalStrategy.getMaxConnections();
    }
    
    public int getIdleConnectionsInPool() {
        int total = 0;
        for (HttpClient<I, O> client: getCurrentHttpClients().values()) {
            PoolStats poolStats = client.getStats();
            total += poolStats.getIdleCount();
        }
        return total;
    }
    
    public GlobalPoolStats getGlobalPoolStats() {
        return stats;
    }
}
