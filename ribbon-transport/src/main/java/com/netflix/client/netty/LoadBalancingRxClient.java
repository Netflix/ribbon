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
package com.netflix.client.netty;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.ssl.AbstractSslContextFactory;
import com.netflix.client.ssl.ClientSslSocketFactoryException;
import com.netflix.client.ssl.URLSslContextFactory;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.LoadBalancerExecutor;
import com.netflix.loadbalancer.LoadBalancerObservableCommand;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerListChangeListener;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.ClientMetricsEvent;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.metrics.MetricEventsSubject;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;
import rx.functions.Func1;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class LoadBalancingRxClient<I, O, T extends RxClient<I, O>> implements RxClient<I, O> {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancingRxClient.class);
    protected final ConcurrentMap<Server, T> rxClientCache;
    protected final LoadBalancerExecutor lbExecutor;
    protected final PipelineConfigurator<O, I> pipelineConfigurator;
    protected final IClientConfig clientConfig;
    protected final RetryHandler retryHandler;
    protected final AbstractSslContextFactory sslContextFactory;
    protected final MetricEventsListener<? extends ClientMetricsEvent<?>> listener;
    protected final MetricEventsSubject<ClientMetricsEvent<?>> eventSubject;

    public LoadBalancingRxClient(IClientConfig config, RetryHandler retryHandler, PipelineConfigurator<O, I> pipelineConfigurator) {
        this(LoadBalancerBuilder.newBuilder().withClientConfig(config).buildLoadBalancerFromConfigWithReflection(),
                config,
                retryHandler,
                pipelineConfigurator
                );
    }
    
    public LoadBalancingRxClient(ILoadBalancer lb, IClientConfig config, RetryHandler retryHandler, PipelineConfigurator<O, I> pipelineConfigurator) {
        rxClientCache = new ConcurrentHashMap<Server, T>();
        lbExecutor = new LoadBalancerExecutor(lb, config, retryHandler);
        this.retryHandler = retryHandler;
        this.pipelineConfigurator = pipelineConfigurator;
        this.clientConfig = config;
        this.listener = createListener(config.getClientName());
        eventSubject = new MetricEventsSubject<ClientMetricsEvent<?>>();
        boolean isSecure = getProperty(IClientConfigKey.Keys.IsSecure, null, false); 
        if (isSecure) {
            final URL trustStoreUrl = getResourceForOptionalProperty(CommonClientConfigKey.TrustStore);
            final URL keyStoreUrl = getResourceForOptionalProperty(CommonClientConfigKey.KeyStore);
            boolean isClientAuthRequired = clientConfig.get(IClientConfigKey.Keys.IsClientAuthRequired, false);
            if (    // if client auth is required, need both a truststore and a keystore to warrant configuring
                    // if client is not is not required, we only need a keystore OR a truststore to warrant configuring
                    (isClientAuthRequired && (trustStoreUrl != null && keyStoreUrl != null))
                    ||
                    (!isClientAuthRequired && (trustStoreUrl != null || keyStoreUrl != null))
                    ) {

                try {
                    sslContextFactory = new URLSslContextFactory(trustStoreUrl,
                            clientConfig.get(CommonClientConfigKey.TrustStorePassword),
                            keyStoreUrl,
                            clientConfig.get(CommonClientConfigKey.KeyStorePassword));

                } catch (ClientSslSocketFactoryException e) {
                    throw new IllegalArgumentException("Unable to configure custom secure socket factory", e);
                }
            } else {
                sslContextFactory = null;
            }
        } else {
            sslContextFactory = null;
        }

        addLoadBalancerListener();
    }
      
    public IClientConfig getClientConfig() {
        return clientConfig;
    }

    public <T> Observable<T> connectWithAction(final Func1<ObservableConnection<O, I>, Observable<T>> action, RetryHandler retryHandler) {
        return lbExecutor.create(new LoadBalancerObservableCommand<T>() {
            @Override
            public Observable<T> run(Server server) {
                return getRxClient(server.getHost(), server.getPort()).connect().flatMap(action);
            }
        }, retryHandler == null ? this.retryHandler : retryHandler);
    }

    public <T> Observable<T> connectWithAction(final Func1<ObservableConnection<O, I>, Observable<T>> action) {
        return connectWithAction(action, retryHandler);
    }

    public int getResponseTimeOut() {
        int maxRetryNextServer = 0;
        int maxRetrySameServer = 0;
        if (retryHandler != null) {
            maxRetryNextServer = retryHandler.getMaxRetriesOnNextServer();
            maxRetrySameServer = retryHandler.getMaxRetriesOnSameServer();
        } else {
            maxRetryNextServer = clientConfig.get(IClientConfigKey.Keys.MaxAutoRetriesNextServer, DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER);
            maxRetrySameServer = clientConfig.get(IClientConfigKey.Keys.MaxAutoRetries, DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES);
        }
        int readTimeout = getProperty(IClientConfigKey.Keys.ReadTimeout, null, DefaultClientConfigImpl.DEFAULT_READ_TIMEOUT);
        int connectTimeout = getProperty(IClientConfigKey.Keys.ConnectTimeout, null, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        return (maxRetryNextServer + 1) * (maxRetrySameServer + 1) * (readTimeout + connectTimeout);
    }
    
    public int getMaxConcurrentRequests() {
        return -1;
    }
        
    protected <S> S getProperty(IClientConfigKey<S> key, @Nullable IClientConfig requestConfig, S defaultValue) {
        if (requestConfig != null && requestConfig.get(key) != null) {
            return requestConfig.get(key);
        } else {
            return clientConfig.get(key, defaultValue);
        }
    }

    protected URL getResourceForOptionalProperty(final IClientConfigKey<String> configKey) {
        final String propValue = clientConfig.get(configKey);
        URL result = null;

        if (propValue != null) {
            result = getResource(propValue);
            if (result == null) {
                throw new IllegalArgumentException("No resource found for " + configKey + ": "
                        + propValue);
            }
        }
        return result;
    }

    private static URL getResource(String resourceName)
    {
        URL url = null;
        // attempt to load from the context classpath
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            url = loader.getResource(resourceName);
        }
        if (url == null) {
            // attempt to load from the system classpath
            url = ClassLoader.getSystemResource(resourceName);
        }
        if (url == null) {
            try {
                resourceName = URLDecoder.decode(resourceName, "UTF-8");
                url = (new File(resourceName)).toURI().toURL();
            } catch (Exception e) {
                logger.error("Problem loading resource", e);
            }
        }
        return url;
    }

    /**
     * This is where we remove HttpClient and shutdown its connection pool if it is no longer available from load balancer.
     */
    private void addLoadBalancerListener() {
        ILoadBalancer lb = lbExecutor.getLoadBalancer();
        if (!(lb instanceof BaseLoadBalancer)) {
            return;
        }
        ((BaseLoadBalancer) lb).addServerListChangeListener(new ServerListChangeListener() {
            @Override
            public void serverListChanged(List<Server> oldList, List<Server> newList) {
                Set<Server> removedServers = new HashSet<Server>(oldList);
                removedServers.removeAll(newList);
                for (Server server: rxClientCache.keySet()) {
                    if (removedServers.contains(server)) {
                        // this server is no longer in UP status
                        removeClient(server);
                    }
                }
            }
        });
    }

    protected abstract T cacheLoadRxClient(Server server);
    
    protected T getRxClient(String host, int port) {
        Server server = new Server(host, port);
        T client =  rxClientCache.get(server);
        if (client != null) {
            return client;
        } else {
            client = cacheLoadRxClient(server);
            client.subscribe(listener);
            client.subscribe(eventSubject);
            T old = rxClientCache.putIfAbsent(server, client);
            if (old != null) {
                return old;
            } else {
                return client;
            }
        }
    }
    
    protected T removeClient(Server server) {
        T client = rxClientCache.remove(server);
        client.shutdown();
        return client;
    }
    
    @Override
    public Observable<ObservableConnection<O, I>> connect() {
        return lbExecutor.create(new LoadBalancerObservableCommand<ObservableConnection<O, I>>() {
            @Override
            public Observable<ObservableConnection<O, I>> run(
                    Server server) {
                return getRxClient(server.getHost(), server.getPort()).connect();
            }
        });
    }

    protected abstract MetricEventsListener<? extends ClientMetricsEvent<?>> createListener(String name);
    
    @Override
    public void shutdown() {
        for (Server server: rxClientCache.keySet()) {
            removeClient(server);
        }
    }

    @Override
    public String name() {
        return clientConfig.getClientName();
    }

    @Override
    public Subscription subscribe(
            MetricEventsListener<? extends ClientMetricsEvent<?>> listener) {
       return eventSubject.subscribe(listener);
    }

    public final LoadBalancerContext getLoadBalancerContext() {
        return lbExecutor;
    }
}
