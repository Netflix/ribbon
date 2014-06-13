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
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.CompositePoolLimitDeterminationStrategy;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.contexts.RxContexts;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import rx.Observable;
import rx.functions.Func1;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.netflix.client.ClientException;
import com.netflix.client.ClientException.ErrorType;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.netty.CachedPooledRxClient;
import com.netflix.loadbalancer.ClientObservableProvider;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;

/**
 * A Netty HttpClient that can connect to different servers. Internally it caches the RxNetty's HttpClient, with each created with 
 * a connection pool governed by {@link CompositePoolLimitDeterminationStrategy} that has a global limit and per server limit. 
 *   
 * @author awang
 */
public class NettyHttpClient<I, O> extends CachedPooledRxClient<HttpClientRequest<I>, HttpClientResponse<O>, HttpClient<I, O>>
        implements HttpClient<I, O> {
    protected static final PipelineConfigurator<HttpClientResponse<ByteBuf>, HttpClientRequest<ByteBuf>> DEFAULT_PIPELINE_CONFIGURATOR = 
            PipelineConfigurators.httpClientConfigurator();
    protected static final PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<ByteBuf>> DEFAULT_SSE_PIPELINE_CONFIGURATOR = 
            PipelineConfigurators.sseClientConfigurator();
    
    public static NettyHttpClient<ByteBuf, ByteBuf> createDefaultHttpClient() {
        return createDefaultHttpClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues());
    }
    
    public static NettyHttpClient<ByteBuf, ByteBuf> createDefaultHttpClient(ILoadBalancer lb) {
        return createDefaultHttpClient(lb, 
                DefaultClientConfigImpl.getClientConfigWithDefaultValues(), null);
    }

    public static NettyHttpClient<ByteBuf, ByteBuf> createDefaultHttpClient(ILoadBalancer lb, IClientConfig config) {
        return createDefaultHttpClient(lb, config, null);
    }

    public static NettyHttpClient<ByteBuf, ByteBuf> createDefaultHttpClient(IClientConfig config) {
        ILoadBalancer lb = LoadBalancerBuilder.newBuilder()
                .withClientConfig(config)
                .buildLoadBalancerFromConfigWithReflection();
        return createDefaultHttpClient(lb, config, null);
    }
    
    public static NettyHttpClient<ByteBuf, ByteBuf> createDefaultHttpClient(ILoadBalancer lb, IClientConfig config, RetryHandler handler) {
        return new CachedHttpClientWithConnectionPool<ByteBuf, ByteBuf>(lb, config, 
                NettyHttpClient.DEFAULT_PIPELINE_CONFIGURATOR, handler);
    }
    
    public static <I, O> NettyHttpClient<I, O> createHttpClient(ILoadBalancer lb, PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator) {
        return createHttpClient(lb, pipelineConfigurator, DefaultClientConfigImpl.getClientConfigWithDefaultValues(), null);    
    }
    
    public static <I, O> NettyHttpClient<I, O> createHttpClient(ILoadBalancer lb, PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator, 
            IClientConfig config, RetryHandler handler) {
        return new CachedHttpClientWithConnectionPool<I, O>(lb, config, 
                pipelineConfigurator, handler);
    }

    public static NettyHttpClient<ByteBuf, ServerSentEvent> createDefaultSSEClient(ILoadBalancer lb) {
        return createDefaultSSEClient(lb, DefaultClientConfigImpl.getClientConfigWithDefaultValues(), null);
    }

    public static NettyHttpClient<ByteBuf, ServerSentEvent> createDefaultSSEClient(ILoadBalancer lb, IClientConfig config) {
        return createDefaultSSEClient(lb, config, null);
    }

    public static NettyHttpClient<ByteBuf, ServerSentEvent> createDefaultSSEClient(IClientConfig config) {
        ILoadBalancer lb = LoadBalancerBuilder.newBuilder()
                .withClientConfig(config)
                .buildLoadBalancerFromConfigWithReflection();
        return createDefaultSSEClient(lb, config);
    }
    
    public static NettyHttpClient<ByteBuf, ServerSentEvent> createDefaultSSEClient() {
        return createDefaultSSEClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues());
    }

    public static NettyHttpClient<ByteBuf, ServerSentEvent> createDefaultSSEClient(ILoadBalancer lb, IClientConfig config, RetryHandler handler) {
        return new SSEClient<ByteBuf>(lb, config, DEFAULT_SSE_PIPELINE_CONFIGURATOR, handler);
    }
    
    public NettyHttpClient(ILoadBalancer lb, PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipeLineConfigurator,
            ScheduledExecutorService poolCleanerScheduler) {
        this(lb, DefaultClientConfigImpl.getClientConfigWithDefaultValues(), new NettyHttpLoadBalancerErrorHandler(), pipeLineConfigurator, poolCleanerScheduler);
    }
    
    public NettyHttpClient(
            ILoadBalancer lb,
            IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
            ScheduledExecutorService poolCleanerScheduler) {
        super(lb, config, retryHandler, pipelineConfigurator, poolCleanerScheduler);
        // TODO Auto-generated constructor stub
    }

    private RequestSpecificRetryHandler getRequestRetryHandler(HttpClientRequest<?> request, IClientConfig requestConfig) {
        boolean okToRetryOnAllErrors = request.getMethod().equals(HttpMethod.GET);
        return new RequestSpecificRetryHandler(true, okToRetryOnAllErrors, lbExecutor.getErrorHandler(), requestConfig);
    }
        
    protected void setHost(HttpClientRequest<?> request, String host) {
        request.getHeaders().set(HttpHeaders.Names.HOST, host);
    }

    protected static <I> RepeatableContentHttpRequest<I> getRepeatableRequest(HttpClientRequest<I> original) {
        if (original instanceof RepeatableContentHttpRequest) {
            return (RepeatableContentHttpRequest<I>) original;
        }
        return new RepeatableContentHttpRequest<I>(original);
    }

    public Observable<HttpClientResponse<O>> submit(String host, int port, final HttpClientRequest<I> request) {
        return submit(host, port, request, getRxClientConfig(null));
    }
       
    /**
     * Submit the request. If the server returns 503, it will emit {@link ClientException} as an error from the returned {@link Observable}.
     *  
     * @return
     */
    public Observable<HttpClientResponse<O>> submit(String host, int port, final HttpClientRequest<I> request, ClientConfig rxClientConfig) {
        Preconditions.checkNotNull(host);
        Preconditions.checkNotNull(request);
        HttpClient<I,O> rxClient = getRxClient(host, port);
        setHost(request, host);
        return rxClient.submit(request, rxClientConfig).flatMap(new Func1<HttpClientResponse<O>, Observable<HttpClientResponse<O>>>() {
            @Override
            public Observable<HttpClientResponse<O>> call(
                    HttpClientResponse<O> t1) {
                if (t1.getStatus().code() == 503) {
                    return Observable.error(new ClientException(ErrorType.SERVER_THROTTLED, t1.getStatus().reasonPhrase()));
                } else {
                    return Observable.from(t1);
                }
            }
        });        
    }
    
    private RxClient.ClientConfig getRxClientConfig(IClientConfig requestConfig) {
        if (requestConfig == null) {
            return HttpClientConfig.Builder.newDefaultConfig();
        }
        int requestReadTimeout = getProperty(IClientConfigKey.CommonKeys.ReadTimeout, requestConfig, 
                DefaultClientConfigImpl.DEFAULT_READ_TIMEOUT);
        Boolean followRedirect = getProperty(IClientConfigKey.CommonKeys.FollowRedirects, requestConfig, null);
        HttpClientConfig.Builder builder = new HttpClientConfig.Builder().readTimeout(requestReadTimeout, TimeUnit.MILLISECONDS);
        if (followRedirect != null) {
            builder.setFollowRedirect(followRedirect);
        }
        return builder.build();        
    }
    
    public Observable<HttpClientResponse<O>> submit(String host, int port, final HttpClientRequest<I> request, @Nullable final IClientConfig requestConfig) {
        return submit(host, port, request, getRxClientConfig(requestConfig));
    }

    
    /**
     * Submit a request to server chosen by the load balancer to execute. An error will be emitted from the returned {@link Observable} if 
     * there is no server available from load balancer.
     * 
     * @param errorHandler A handler to determine the load balancer retry logic. If null, the default one will be used.
     * @param requestConfig An {@link IClientConfig} to override the default configuration for the client. Can be null.
     * @return
     */
    public Observable<HttpClientResponse<O>> submit(final HttpClientRequest<I> request, final RetryHandler errorHandler, final IClientConfig requestConfig) {
        final RepeatableContentHttpRequest<I> repeatableRequest = getRepeatableRequest(request);
        final RetryHandler retryHandler = (errorHandler == null) ? getRequestRetryHandler(request, requestConfig) : errorHandler;
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<HttpClientResponse<O>>() {
            @Override
            public Observable<HttpClientResponse<O>> getObservableForEndpoint(
                    Server server) {
                return submit(server.getHost(), server.getPort(), repeatableRequest, requestConfig);
            }
        }, retryHandler);
    }
    
    @VisibleForTesting
    ServerStats getServerStats(Server server) {
        return lbExecutor.getServerStats(server);
    }

    /**
     * Submit a request to server chosen by the load balancer to execute. An error will be emitted from the returned {@link Observable} if 
     * there is no server available from load balancer.
     */
    @Override
    public Observable<HttpClientResponse<O>> submit(HttpClientRequest<I> request) {
        return submit(request, null, null);
    }

    /**
     * Submit a request to server chosen by the load balancer to execute. An error will be emitted from the returned {@link Observable} if 
     * there is no server available from load balancer.
     * 
     * @param config An {@link ClientConfig} to override the default configuration for the client. Can be null.
     * @return
     */
    @Override
    public Observable<HttpClientResponse<O>> submit(HttpClientRequest<I> request, final ClientConfig config) {
        final RepeatableContentHttpRequest<I> repeatableRequest = getRepeatableRequest(request);
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<HttpClientResponse<O>>() {
            @Override
            public Observable<HttpClientResponse<O>> getObservableForEndpoint(
                    Server server) {
                return submit(server.getHost(), server.getPort(), repeatableRequest, config);
            }
        });
    }

    /**
     * Create an {@link ObservableConnection} with a server chosen by the load balancer. 
     */
    @Override
    public Observable<ObservableConnection<HttpClientResponse<O>, HttpClientRequest<I>>> connect() {
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<ObservableConnection<HttpClientResponse<O>, HttpClientRequest<I>>>() {
            @Override
            public Observable<ObservableConnection<HttpClientResponse<O>, HttpClientRequest<I>>> getObservableForEndpoint(
                    Server server) {
                HttpClient<I, O> rxClient = getRxClient(server.getHost(), server.getPort());
                return rxClient.connect();
            }
        });
    }

    @Override
    protected HttpClient<I, O> cacheLoadRxClient(Server server) {
        String requestIdHeaderName = getProperty(IClientConfigKey.CommonKeys.RequestIdHeaderName, null, DefaultClientConfigImpl.DEFAULT_REQUEST_ID_HEADER_NAME);
        HttpClientBuilder<I, O> clientBuilder = RxContexts.newHttpClientBuilder(server.getHost(), server.getPort(), requestIdHeaderName, RxContexts.DEFAULT_CORRELATOR);
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
}
