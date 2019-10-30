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

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.client.ClientMetricsEvent;
import io.reactivex.netty.client.CompositePoolLimitDeterminationStrategy;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.contexts.RxContexts;
import io.reactivex.netty.contexts.http.HttpRequestIdProvider;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.ssl.DefaultFactories;
import io.reactivex.netty.pipeline.ssl.SSLEngineFactory;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.servo.http.HttpClientListener;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLEngine;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.ssl.ClientSslSocketFactoryException;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.loadbalancer.reactive.ExecutionContext;
import com.netflix.loadbalancer.reactive.ExecutionListener;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import com.netflix.loadbalancer.reactive.ServerOperation;
import com.netflix.ribbon.transport.netty.LoadBalancingRxClientWithPoolOptions;

/**
 * A Netty HttpClient that can connect to different servers. Internally it caches the RxNetty's HttpClient, with each created with 
 * a connection pool governed by {@link CompositePoolLimitDeterminationStrategy} that has a global limit and per server limit. 
 *   
 * @author awang
 */
public class LoadBalancingHttpClient<I, O> extends LoadBalancingRxClientWithPoolOptions<HttpClientRequest<I>, HttpClientResponse<O>, HttpClient<I, O>>
        implements HttpClient<I, O> {

    private static final HttpClientConfig DEFAULT_RX_CONFIG = HttpClientConfig.Builder.newDefaultConfig();
    
    private final String requestIdHeaderName;
    private final HttpRequestIdProvider requestIdProvider;
    private final List<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>> listeners;
    private final LoadBalancerCommand<HttpClientResponse<O>> defaultCommandBuilder;
    private final Func2<HttpClientResponse<O>, Integer, Observable<HttpClientResponse<O>>> responseToErrorPolicy;
    private final Func1<Integer, Integer> backoffStrategy;
    
    public static class Builder<I, O> {
        ILoadBalancer lb;
        IClientConfig config;
        RetryHandler retryHandler;
        PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator;
        ScheduledExecutorService poolCleanerScheduler;
        List<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>> listeners;
        Func2<HttpClientResponse<O>, Integer, Observable<HttpClientResponse<O>>> responseToErrorPolicy;
        Func1<Integer, Integer> backoffStrategy;
        Func1<Builder<I, O>, LoadBalancingHttpClient<I, O>> build;
        
        protected Builder(Func1<Builder<I, O>, LoadBalancingHttpClient<I, O>> build) {
            this.build = build;
        }
        
        public Builder<I, O> withLoadBalancer(ILoadBalancer lb) {
            this.lb = lb;
            return this;
        }
        
        public Builder<I, O> withClientConfig(IClientConfig config) {
            this.config = config;
            return this;
        }
        
        public Builder<I, O> withRetryHandler(RetryHandler retryHandler) {
            this.retryHandler = retryHandler;
            return this;
        }
        
        public Builder<I, O> withPipelineConfigurator(PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator) {
            this.pipelineConfigurator = pipelineConfigurator;
            return this;
        }
        
        public Builder<I, O> withPoolCleanerScheduler(ScheduledExecutorService poolCleanerScheduler) {
            this.poolCleanerScheduler = poolCleanerScheduler;
            return this;
        }
        
        public Builder<I, O> withExecutorListeners(List<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>> listeners) {
            this.listeners = listeners;
            return this;
        }
        
        /**
         * Policy for converting a response to an error if the status code indicates it as such.  This will only
         * be called for responses with status code 4xx or 5xx
         * 
         * Parameters to the function are
         * * HttpClientResponse<O> - The actual response
         * * Integer - Backoff to apply if this is a retryable error.  The backoff amount is in milliseconds
         *             and is based on the configured BackoffStrategy.  It is the responsibility of this function
         *             to implement the actual backoff mechanism.  This can be done as Observable.error(e).delay(backoff, TimeUnit.MILLISECONDS)
         * The return Observable will either contain the HttpClientResponse if is it not an error or an 
         * Observable.error() with the translated exception.
         * 
         * @param responseToErrorPolicy
         */
        public Builder<I, O> withResponseToErrorPolicy(Func2<HttpClientResponse<O>, Integer, Observable<HttpClientResponse<O>>> responseToErrorPolicy) {
            this.responseToErrorPolicy = responseToErrorPolicy;
            return this;
        }
        
        /**
         * Strategy for calculating the backoff based on the number of reties.  Input is the number
         * of retries and output is the backoff amount in milliseconds.
         * The default implementation is non random exponential backoff with time interval configurable
         * via the property BackoffInterval (default 1000 msec)
         * 
         * @param BackoffStrategy
         */
        public Builder<I, O> withBackoffStrategy(Func1<Integer, Integer> backoffStrategy) {
            this.backoffStrategy = backoffStrategy;
            return this;
        }
        
        public LoadBalancingHttpClient<I, O> build() {
            if (retryHandler == null) {
                retryHandler = new NettyHttpLoadBalancerErrorHandler();
            }
            if (config == null) {
                config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
            }
            if (lb == null) {
                lb = LoadBalancerBuilder.newBuilder().withClientConfig(config).buildLoadBalancerFromConfigWithReflection();
            }
            if (listeners == null) {
                listeners = Collections.<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>>emptyList();
            }
            if (backoffStrategy == null) {
                backoffStrategy = new Func1<Integer, Integer>() {
                    @Override
                    public Integer call(Integer backoffCount) {
                        int interval = config.getOrDefault(IClientConfigKey.Keys.BackoffInterval);
                        if (backoffCount < 0) {
                            backoffCount = 0;
                        }
                        else if (backoffCount > 10) {   // Reasonable upper bound
                            backoffCount = 10;
                        }
                        return (int)Math.pow(2, backoffCount) * interval;
                    }
                };
            }
            if (responseToErrorPolicy == null) {
                responseToErrorPolicy = new DefaultResponseToErrorPolicy<O>();
            }
            return build.call(this);
        }
    }
    
    public static <I, O> Builder<I, O> builder() {
        return new Builder<I, O>(new Func1<Builder<I, O>, LoadBalancingHttpClient<I, O>>() {
            @Override
            public LoadBalancingHttpClient<I, O> call(Builder<I, O> builder) {
                return new LoadBalancingHttpClient<I, O>(builder);
            }
        });
    }
    
    protected LoadBalancingHttpClient(Builder<I, O> builder) {
        super(builder.lb, builder.config, new RequestSpecificRetryHandler(true, true, builder.retryHandler, null), builder.pipelineConfigurator, builder.poolCleanerScheduler);
        requestIdHeaderName = getProperty(IClientConfigKey.Keys.RequestIdHeaderName, null, null);
        requestIdProvider = (requestIdHeaderName != null) 
                          ? new HttpRequestIdProvider(requestIdHeaderName, RxContexts.DEFAULT_CORRELATOR)
                          : null;
        this.listeners = new CopyOnWriteArrayList<ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>>>(builder.listeners);
        defaultCommandBuilder = LoadBalancerCommand.<HttpClientResponse<O>>builder()
                .withLoadBalancerContext(lbContext)
                .withListeners(this.listeners)
                .withClientConfig(builder.config)
                .withRetryHandler(builder.retryHandler)
                .build();
        this.responseToErrorPolicy = builder.responseToErrorPolicy;
        this.backoffStrategy = builder.backoffStrategy;
    }

    private RetryHandler getRequestRetryHandler(HttpClientRequest<?> request, IClientConfig requestConfig) {
        return new RequestSpecificRetryHandler(
                true, 
                request.getMethod().equals(HttpMethod.GET),     // Default only allows retrys for GET
                defaultRetryHandler, 
                requestConfig);
    }

    protected static void setHostHeader(HttpClientRequest<?> request, String host) {
        request.getHeaders().set(HttpHeaders.Names.HOST, host);
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
        return submit(null, request, null, null, config);
    }

    /**
     * Submit a request to run on a specific server
     * 
     * @param server
     * @param request
     * @param requestConfig
     * @return
     */
    public Observable<HttpClientResponse<O>> submit(Server server, final HttpClientRequest<I> request, final IClientConfig requestConfig) {
        return submit(server, request, null, requestConfig, getRxClientConfig(requestConfig));
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
        return submit(null, request, errorHandler, requestConfig, null);
    }
    
    public Observable<HttpClientResponse<O>> submit(Server server, final HttpClientRequest<I> request) {
        return submit(server, request, null, null, getRxClientConfig(null));
    }

    /**
     * Convert an HttpClientRequest to a ServerOperation 
     * 
     * @param server
     * @param request
     * @param rxClientConfig
     * @return
     */
    protected ServerOperation<HttpClientResponse<O>> requestToOperation(final HttpClientRequest<I> request, final ClientConfig rxClientConfig) {
        Preconditions.checkNotNull(request);
        
        return new ServerOperation<HttpClientResponse<O>>() {
            final AtomicInteger count = new AtomicInteger(0);
            
            @Override
            public Observable<HttpClientResponse<O>> call(Server server) {
                HttpClient<I,O> rxClient = getOrCreateRxClient(server);
                setHostHeader(request, server.getHost());
                
                Observable<HttpClientResponse<O>> o;
                if (rxClientConfig != null) {
                    o = rxClient.submit(request, rxClientConfig);
                } 
                else {
                    o = rxClient.submit(request);
                }
                
                return o.concatMap(new Func1<HttpClientResponse<O>, Observable<HttpClientResponse<O>>>() {
                    @Override
                    public Observable<HttpClientResponse<O>> call(HttpClientResponse<O> t1) {
                        if (t1.getStatus().code()/100 == 4 || t1.getStatus().code()/100 == 5)
                            return responseToErrorPolicy.call(t1, backoffStrategy.call(count.getAndIncrement()));
                        else
                            return Observable.just(t1);
                    }
                });
            }
        };
    }
    
    /** 
     * Construct an RxClient.ClientConfig from an IClientConfig
     * 
     * @param requestConfig
     * @return
     */
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
        } 
        else if (rxClientConfig == null) {
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
        } 
        else {
            RxClient.ClientConfig.Builder builder = new RxClient.ClientConfig.Builder(rxClientConfig);
            if (readTimeoutFormRibbon >= 0) {
                builder.readTimeout(readTimeoutFormRibbon, TimeUnit.MILLISECONDS);
            }
            return builder.build();
        }
    }

    private IClientConfig getRibbonClientConfig(ClientConfig rxClientConfig) {
        if (rxClientConfig != null && rxClientConfig.isReadTimeoutSet()) {
            return IClientConfig.Builder.newBuilder().withReadTimeout((int) rxClientConfig.getReadTimeoutInMillis()).build();
        }
        return null;
    }

    /**
     * Subject an operation to run in the load balancer
     * 
     * @param request
     * @param errorHandler
     * @param requestConfig
     * @param rxClientConfig
     * @return
     */
    private Observable<HttpClientResponse<O>> submit(final Server server, final HttpClientRequest<I> request, final RetryHandler errorHandler, final IClientConfig requestConfig, final ClientConfig rxClientConfig) {
        RetryHandler retryHandler = errorHandler;
        if (retryHandler == null) {
            retryHandler = getRequestRetryHandler(request, requestConfig);
        }
        
        final IClientConfig config = requestConfig == null ? DefaultClientConfigImpl.getEmptyConfig() : requestConfig;
        final ExecutionContext<HttpClientRequest<I>> context = new ExecutionContext<HttpClientRequest<I>>(request, config, this.getClientConfig(), retryHandler);
        
        Observable<HttpClientResponse<O>> result = submitToServerInURI(request, config, rxClientConfig, retryHandler, context);
        if (result == null) {
            LoadBalancerCommand<HttpClientResponse<O>> command;
            if (retryHandler != defaultRetryHandler) {
                // need to create new builder instead of the default one
                command = LoadBalancerCommand.<HttpClientResponse<O>>builder()
                        .withExecutionContext(context)
                        .withLoadBalancerContext(lbContext)
                        .withListeners(listeners)
                        .withClientConfig(this.getClientConfig())
                        .withRetryHandler(retryHandler)
                        .withServer(server)
                        .build();
            }
            else {
                command = defaultCommandBuilder;
            }
            
            result = command.submit(requestToOperation(request, getRxClientConfig(config, rxClientConfig)));
        }
        return result;
    }

    @VisibleForTesting
    ServerStats getServerStats(Server server) {
        return lbContext.getServerStats(server);
    }

    /**
     * Submits the request to the server indicated in the URI
     * @param request
     * @param requestConfig
     * @param config
     * @param errorHandler
     * @param context
     * @return
     */
    private Observable<HttpClientResponse<O>> submitToServerInURI(
            HttpClientRequest<I> request, IClientConfig requestConfig, ClientConfig config,
            RetryHandler errorHandler, ExecutionContext<HttpClientRequest<I>> context)  {
        // First, determine server from the URI
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
            if (clientConfig.get(IClientConfigKey.Keys.IsSecure, false)) {
                port = 443;
            } else {
                port = 80;
            }
        }
        
        return LoadBalancerCommand.<HttpClientResponse<O>>builder()
                .withRetryHandler(errorHandler)
                .withLoadBalancerContext(lbContext)
                .withListeners(listeners)
                .withExecutionContext(context)
                .withServer(new Server(host, port))
                .build()
                .submit(this.requestToOperation(request, getRxClientConfig(requestConfig, config)));
    }
    
    @Override
    protected HttpClient<I, O> createRxClient(Server server) {
        HttpClientBuilder<I, O> clientBuilder;
        if (requestIdProvider != null) {
            clientBuilder = RxContexts.<I, O>newHttpClientBuilder(server.getHost(), server.getPort(), 
                    requestIdProvider, RxContexts.DEFAULT_CORRELATOR, pipelineConfigurator);
        } else {
            clientBuilder = RxContexts.<I, O>newHttpClientBuilder(server.getHost(), server.getPort(), 
                    RxContexts.DEFAULT_CORRELATOR, pipelineConfigurator);
        }
        Integer connectTimeout = getProperty(IClientConfigKey.Keys.ConnectTimeout,  null, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        Integer readTimeout    = getProperty(IClientConfigKey.Keys.ReadTimeout,     null, DefaultClientConfigImpl.DEFAULT_READ_TIMEOUT);
        Boolean followRedirect = getProperty(IClientConfigKey.Keys.FollowRedirects, null, null);
        HttpClientConfig.Builder builder = new HttpClientConfig.Builder().readTimeout(readTimeout, TimeUnit.MILLISECONDS);
        if (followRedirect != null) {
            builder.setFollowRedirect(followRedirect);
        }
        clientBuilder
                .channelOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .config(builder.build());
        if (isPoolEnabled()) {
            clientBuilder
                .withConnectionPoolLimitStrategy(poolStrategy)
                .withIdleConnectionsTimeoutMillis(idleConnectionEvictionMills)
                .withPoolIdleCleanupScheduler(poolCleanerScheduler);
        } 
        else {
            clientBuilder
                .withNoConnectionPooling();
        }
        
        if (sslContextFactory != null) {
            try {
                SSLEngineFactory myFactory = new DefaultFactories.SSLContextBasedFactory(sslContextFactory.getSSLContext()) {
                    @Override
                    public SSLEngine createSSLEngine(ByteBufAllocator allocator) {
                        SSLEngine myEngine = super.createSSLEngine(allocator);
                        myEngine.setUseClientMode(true);
                        return myEngine;
                    }
                };

                clientBuilder.withSslEngineFactory(myFactory);
            } catch (ClientSslSocketFactoryException e) {
                throw new RuntimeException(e);
            }
        }
        return clientBuilder.build();
    }
    
    @VisibleForTesting
    HttpClientListener getListener() {
        return (HttpClientListener) listener;
    }

    @VisibleForTesting
    Map<Server, HttpClient<I, O>> getRxClients() {
        return rxClientCache;
    }
    
    @Override
    protected MetricEventsListener<? extends ClientMetricsEvent<?>> createListener(String name) {
        return HttpClientListener.newHttpListener(name);
    }

}
