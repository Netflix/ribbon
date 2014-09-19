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

package com.netflix.ribbon.transport.netty.http;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.netflix.loadbalancer.reactive.ExecutionContext;
import com.netflix.loadbalancer.reactive.ExecutionListener;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.ribbon.transport.netty.LoadBalancingRxClientWithPoolOptions;
import com.netflix.client.ssl.ClientSslSocketFactoryException;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.loadbalancer.reactive.CommandBuilder;
import com.netflix.loadbalancer.reactive.LoadBalancerObservable;
import com.netflix.loadbalancer.reactive.LoadBalancerObservableCommand;
import com.netflix.loadbalancer.reactive.LoadBalancerRetrySameServerCommand;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.client.ClientMetricsEvent;
import io.reactivex.netty.client.CompositePoolLimitDeterminationStrategy;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.client.RxClient.ClientConfig.Builder;
import io.reactivex.netty.contexts.RxContexts;
import io.reactivex.netty.contexts.http.HttpRequestIdProvider;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.ssl.DefaultFactories;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.servo.http.HttpClientListener;
import rx.Observable;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A Netty HttpClient that can connect to different servers. Internally it caches the RxNetty's HttpClient, with each created with 
 * a connection pool governed by {@link CompositePoolLimitDeterminationStrategy} that has a global limit and per server limit. 
 *   
 * @author awang
 */
public class LoadBalancingHttpClient<I, O> extends LoadBalancingRxClientWithPoolOptions<HttpClientRequest<I>, HttpClientResponse<O>, HttpClient<I, O>>
        implements HttpClient<I, O> {

    private String requestIdHeaderName;
    private HttpRequestIdProvider requestIdProvider;
    private final List<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>> listeners;
    private final CommandBuilder<HttpClientResponse<O>> defaultCommandBuilder;
    private static final HttpClientConfig DEFAULT_RX_CONFIG = HttpClientConfig.Builder.newDefaultConfig();

    public LoadBalancingHttpClient(ILoadBalancer lb, PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipeLineConfigurator,
                                   ScheduledExecutorService poolCleanerScheduler) {
        this(lb, DefaultClientConfigImpl.getClientConfigWithDefaultValues(), 
                new NettyHttpLoadBalancerErrorHandler(), pipeLineConfigurator, poolCleanerScheduler,
                Collections.<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>>emptyList());
    }
    
    public LoadBalancingHttpClient(
            IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
            ScheduledExecutorService poolCleanerScheduler) {
        this(LoadBalancerBuilder.newBuilder().withClientConfig(config).buildLoadBalancerFromConfigWithReflection(),
                config, retryHandler, pipelineConfigurator, poolCleanerScheduler, Collections.<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>>emptyList());
    }

    public LoadBalancingHttpClient(
            ILoadBalancer lb,
            IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
            ScheduledExecutorService poolCleanerScheduler) {
        this(lb, config, retryHandler, pipelineConfigurator, poolCleanerScheduler, Collections.<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>>emptyList());
    }

    public LoadBalancingHttpClient(
            ILoadBalancer lb,
            IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
            ScheduledExecutorService poolCleanerScheduler, List<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>> listeners) {
        super(lb, config, new RequestSpecificRetryHandler(true, true, retryHandler, null), pipelineConfigurator, poolCleanerScheduler);
        requestIdHeaderName = getProperty(IClientConfigKey.Keys.RequestIdHeaderName, null, null);
        if (requestIdHeaderName != null) {
            requestIdProvider = new HttpRequestIdProvider(requestIdHeaderName, RxContexts.DEFAULT_CORRELATOR);
        }
        this.listeners = new CopyOnWriteArrayList<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>>(listeners);
        defaultCommandBuilder = CommandBuilder.<HttpClientResponse<O>>newBuilder()
                .withLoadBalancerContext(lbContext)
                .withListeners(this.listeners)
                .withClientConfig(config)
                .withRetryHandler(defaultRetryHandler);
    }

    private RetryHandler getRequestRetryHandler(HttpClientRequest<?> request, IClientConfig requestConfig) {
        boolean okToRetryOnAllErrors = request.getMethod().equals(HttpMethod.GET);
        return new RequestSpecificRetryHandler(true, okToRetryOnAllErrors, defaultRetryHandler, requestConfig);
    }

    protected void setHost(HttpClientRequest<?> request, String host) {
        request.getHeaders().set(HttpHeaders.Names.HOST, host);
    }

    protected Observable<HttpClientResponse<O>> submit(String host, int port, final HttpClientRequest<I> request) {
        return submit(host, port, request, getRxClientConfig(null));
    }
       
    protected Observable<HttpClientResponse<O>> submit(String host, int port, final HttpClientRequest<I> request, ClientConfig rxClientConfig) {
        Preconditions.checkNotNull(host);
        Preconditions.checkNotNull(request);
        HttpClient<I,O> rxClient = getRxClient(host, port);
        setHost(request, host);
        if (rxClientConfig != null) {
            return rxClient.submit(request, rxClientConfig);
        } else {
            return rxClient.submit(request);
        }
    }
    
    private RxClient.ClientConfig getRxClientConfig(IClientConfig requestConfig) {
        if (requestConfig == null) {
            return DEFAULT_RX_CONFIG;
        }
        int requestReadTimeout = getProperty(IClientConfigKey.Keys.ReadTimeout, requestConfig, 
                DefaultClientConfigImpl.DEFAULT_READ_TIMEOUT);
        Boolean followRedirect = getProperty(IClientConfigKey.Keys.FollowRedirects, requestConfig, null);
        HttpClientConfig.Builder builder = new HttpClientConfig.Builder().readTimeout(requestReadTimeout, TimeUnit.MILLISECONDS);
        if (followRedirect != null) {
            builder.setFollowRedirect(followRedirect);
        }
        return builder.build();        
    }

    /**
     * @return ClientConfig that is merged from IClientConfig and ClientConfig in the method arguments
     */
    private RxClient.ClientConfig getRxClientConfig(IClientConfig ribbonClientConfig, ClientConfig rxClientConfig) {
        if (ribbonClientConfig == null) {
            return rxClientConfig;
        } else if (rxClientConfig == null) {
            return getRxClientConfig(ribbonClientConfig);
        }
        int readTimeoutFormRibbon = ribbonClientConfig.get(CommonClientConfigKey.ReadTimeout, -1);
        if (rxClientConfig instanceof HttpClientConfig) {
            HttpClientConfig httpConfig = (HttpClientConfig) rxClientConfig;
            HttpClientConfig.Builder builder = HttpClientConfig.Builder.from(httpConfig);
            if (readTimeoutFormRibbon >= 0) {
                builder.readTimeout(readTimeoutFormRibbon, TimeUnit.MILLISECONDS);
            }
            return builder.build();
        } else {
            Builder builder = new Builder(rxClientConfig);
            if (readTimeoutFormRibbon >= 0) {
                builder.readTimeout(readTimeoutFormRibbon, TimeUnit.MILLISECONDS);
            }
            return builder.build();
        }
    }


    public Observable<HttpClientResponse<O>> submit(String host, int port, final HttpClientRequest<I> request, @Nullable final IClientConfig requestConfig) {
        return submit(host, port, request, getRxClientConfig(requestConfig));
    }

    private Observable<HttpClientResponse<O>> submit(final HttpClientRequest<I> request, final RetryHandler errorHandler, final IClientConfig requestConfig, final ClientConfig rxClientConfig) {
        RetryHandler retryHandler = errorHandler;
        if (retryHandler == null) {
            if (requestConfig != null || !request.getMethod().equals(HttpMethod.GET)) {
                retryHandler = getRequestRetryHandler(request, requestConfig);
            } else {
                retryHandler = defaultRetryHandler;
            }
        }
        final IClientConfig config = requestConfig == null ? DefaultClientConfigImpl.getEmptyConfig() : requestConfig;
        final ExecutionContext<HttpClientRequest<I>> context = new ExecutionContext<HttpClientRequest<I>>(request, config, this.getClientConfig(), retryHandler);
        Observable<HttpClientResponse<O>> result = submitToServerInURI(request, config, rxClientConfig, retryHandler, context);
        if (result != null) {
            return result;
        }
        CommandBuilder<HttpClientResponse<O>> builder = defaultCommandBuilder;
        if (retryHandler != defaultRetryHandler) {
            // need to create new builder instead of the default one
            builder = CommandBuilder.<HttpClientResponse<O>>newBuilder()
                    .withLoadBalancerContext(lbContext)
                    .withListeners(listeners)
                    .withClientConfig(this.getClientConfig())
                    .withRetryHandler(retryHandler);
        }
        LoadBalancerObservableCommand<HttpClientResponse<O>> command = builder.build(new LoadBalancerObservable<HttpClientResponse<O>>() {
                @Override
                public Observable<HttpClientResponse<O>> call(Server server) {
                    return submit(server.getHost(), server.getPort(), request, getRxClientConfig(config, rxClientConfig));
                }
            }, context);
        return command.toObservable();
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
        return submit(request, errorHandler, requestConfig, null);
    }
    
    @VisibleForTesting
    ServerStats getServerStats(Server server) {
        return lbContext.getServerStats(server);
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
    public Observable<HttpClientResponse<O>> submit(final HttpClientRequest<I> request, final ClientConfig config) {
        return submit(request, null, null, config);
    }

    private IClientConfig getRibbonClientConfig(ClientConfig rxClientConfig) {
        if (rxClientConfig != null && rxClientConfig.isReadTimeoutSet()) {
            return IClientConfig.Builder.newBuilder().withReadTimeout((int) rxClientConfig.getReadTimeoutInMillis()).build();
        }
        return null;
    }

    private Observable<HttpClientResponse<O>> submitToServerInURI(HttpClientRequest<I> request, IClientConfig requestConfig, ClientConfig config,
                                                                  RetryHandler errorHandler, ExecutionContext<HttpClientRequest<I>> context)  {
        URI uri;
        try {
            uri = new URI(request.getUri());
        } catch (URISyntaxException e) {
            return Observable.error(e);
        }
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        int port = uri.getPort();
        if (port < 0) {
            if (clientConfig.getPropertyAsBoolean(IClientConfigKey.Keys.IsSecure, false)) {
                port = 443;
            } else {
                port = 80;
            }
        }
        if (errorHandler.getMaxRetriesOnSameServer() == 0) {
            return submit(host, port, request, config);
        }
        Server server = new Server(host, port);
        LoadBalancerRetrySameServerCommand<HttpClientResponse<O>> command = CommandBuilder.<HttpClientResponse<O>>newBuilder()
                .withRetryHandler(errorHandler)
                .withLoadBalancerContext(lbContext)
                .withListeners(listeners)
                .build(context);
        return command.retryWithSameServer(server, submit(server.getHost(), server.getPort(), request, getRxClientConfig(requestConfig, config)));
    }
    
    @Override
    protected HttpClient<I, O> cacheLoadRxClient(Server server) {
        HttpClientBuilder<I, O> clientBuilder;
        if (requestIdProvider != null) {
            clientBuilder = RxContexts.<I, O>newHttpClientBuilder(server.getHost(), server.getPort(), 
                    requestIdProvider, RxContexts.DEFAULT_CORRELATOR, pipelineConfigurator);
        } else {
            clientBuilder = RxContexts.<I, O>newHttpClientBuilder(server.getHost(), server.getPort(), 
                    RxContexts.DEFAULT_CORRELATOR, pipelineConfigurator);
        }
        Integer connectTimeout = getProperty(IClientConfigKey.Keys.ConnectTimeout, null, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        Integer readTimeout = getProperty(IClientConfigKey.Keys.ReadTimeout, null, DefaultClientConfigImpl.DEFAULT_READ_TIMEOUT);
        Boolean followRedirect = getProperty(IClientConfigKey.Keys.FollowRedirects, null, null);
        HttpClientConfig.Builder builder = new HttpClientConfig.Builder().readTimeout(readTimeout, TimeUnit.MILLISECONDS);
        if (followRedirect != null) {
            builder.setFollowRedirect(followRedirect);
        }
        RxClient.ClientConfig rxClientConfig = builder.build();
        clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .config(rxClientConfig);
        if (isPoolEnabled()) {
            clientBuilder.withConnectionPoolLimitStrategy(poolStrategy)
            .withIdleConnectionsTimeoutMillis(idleConnectionEvictionMills)
            .withPoolIdleCleanupScheduler(poolCleanerScheduler);
        } else {
            clientBuilder.withNoConnectionPooling();
        }
        if (sslContextFactory != null) {
            try {
                clientBuilder.withSslEngineFactory(DefaultFactories.fromSSLContext(sslContextFactory.getSSLContext()));
            } catch (ClientSslSocketFactoryException e) {
                throw new RuntimeException(e);
            }
        }
        HttpClient<I, O> client = clientBuilder.build();
        return client;
    }
    
    HttpClientListener getListener() {
        return (HttpClientListener) listener;
    }

    Map<Server, HttpClient<I, O>> getRxClients() {
        return rxClientCache;
    }
    
    @Override
    protected MetricEventsListener<? extends ClientMetricsEvent<?>> createListener(
            String name) {
        return HttpClientListener.newHttpListener(name);
    }

}
