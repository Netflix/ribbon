package com.netflix.client.netty.http;

import io.netty.channel.ChannelOption;
import io.reactivex.netty.client.CompositePoolLimitDeterminationStrategy;
import io.reactivex.netty.client.MaxConnectionsBasedStrategy;
import io.reactivex.netty.client.PoolStats;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.contexts.RxContexts;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.config.IClientConfigKey.CommonKeys;
import com.netflix.client.netty.DynamicPropertyBasedPoolStrategy;
import com.netflix.config.DynamicIntProperty;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerListChangeListener;
import com.netflix.utils.ScheduledThreadPoolExectuorWithDynamicSize;

/**
 * A {@link HttpClient} that caches the HttpClient it creates for each {@link Server}, with each created with 
 * a connection pool governed by {@link CompositePoolLimitDeterminationStrategy} that has a global limit and per server limit. 
 *   
 * @author awang
 */
class CachedHttpClientWithConnectionPool<I, O> extends NettyHttpClient<I, O>  {

    private static final ScheduledExecutorService poolCleanerScheduler;
    private static final DynamicIntProperty POOL_CLEANER_CORE_SIZE = new DynamicIntProperty("rxNetty.poolCleaner.coreSize", 2);
    @SuppressWarnings("rawtypes")
    protected ConcurrentMap<Server, HttpClient> rxClientCache;
    protected final CompositePoolLimitDeterminationStrategy poolStrategy;
    protected final MaxConnectionsBasedStrategy globalStrategy;
    protected final int idleConnectionEvictionMills;
    protected final GlobalPoolStats stats;

    static {
        ThreadFactory factory = (new ThreadFactoryBuilder()).setDaemon(true)
                .setNameFormat("RxClient_Connection_Pool_Clean_Up")
                .build();
        poolCleanerScheduler = new ScheduledThreadPoolExectuorWithDynamicSize(POOL_CLEANER_CORE_SIZE, factory);
    }

    public CachedHttpClientWithConnectionPool(
            ILoadBalancer lb,
            IClientConfig config,
            PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
            RetryHandler errorHandler) {
        // super(lb, config, pipelineConfigurator, errorHandler);
        int maxTotalConnections = config.getPropertyWithType(IClientConfigKey.CommonKeys.MaxTotalHttpConnections,
                DefaultClientConfigImpl.DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS);
        int maxConnections = config.getPropertyWithType(CommonKeys.MaxHttpConnectionsPerHost, DefaultClientConfigImpl.DEFAULT_MAX_HTTP_CONNECTIONS_PER_HOST);
        MaxConnectionsBasedStrategy perHostStrategy = new DynamicPropertyBasedPoolStrategy(maxConnections,
                config.getClientName() + "." + config.getNameSpace() + "." + CommonClientConfigKey.MaxHttpConnectionsPerHost);
        globalStrategy = new DynamicPropertyBasedPoolStrategy(maxTotalConnections, 
                config.getClientName() + "." + config.getNameSpace() + "." + CommonClientConfigKey.MaxTotalHttpConnections);
        poolStrategy = new CompositePoolLimitDeterminationStrategy(perHostStrategy, globalStrategy);
        idleConnectionEvictionMills = config.getPropertyWithType(CommonKeys.ConnIdleEvictTimeMilliSeconds, DefaultClientConfigImpl.DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS);
        rxClientCache = new ConcurrentHashMap<Server, HttpClient>();
        stats = new GlobalPoolStats(config.getClientName(), globalStrategy, rxClientCache);
        addLoadBalancerListener();
    }
    
    public CachedHttpClientWithConnectionPool(
            ILoadBalancer lb,
            IClientConfig config,
            PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipeLineConfigurator) {
        this(lb, config, pipeLineConfigurator, null);
    }

    public CachedHttpClientWithConnectionPool(
            ILoadBalancer lb,
            PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipeLineConfigurator) {
        this(lb, DefaultClientConfigImpl.getClientConfigWithDefaultValues(), pipeLineConfigurator);
    }
    
    /**
     * This is where we remove HttpClient and shutdown its connection pool if it is no longer available from load balancer.
     */
    private void addLoadBalancerListener() {
        ILoadBalancer lb = lbExecutor.getLoadBalancer();
        if (!(lb instanceof DynamicServerListLoadBalancer)) {
            return;
        }
        ((DynamicServerListLoadBalancer<?>) lb).addServerListChangeListener(new ServerListChangeListener() {
            @Override
            public void serverListChanged(List<Server> oldList, List<Server> newList) {
                for (Server server: rxClientCache.keySet()) {
                    if (!newList.contains(server)) {
                        // this server is no longer in UP status
                        removeClient(server);
                    }
                }
            }
        });
    }

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
    
    protected HttpClient<I, O> removeClient(Server server) {
        HttpClient<I, O> client = rxClientCache.remove(server);
        client.shutdown();
        return client;
    }

    @Override
    public void close() {
        for (Server server: rxClientCache.keySet()) {
            removeClient(server);
        }
    }

    @Override
    public PoolStats getStats() {
        return stats;
    }

    @Override
    public void shutdown() {
        close();
    }

    @Override
    public Observable<PoolStateChangeEvent> poolStateChangeObservable() {
        return Observable.create(new OnSubscribe<PoolStateChangeEvent>() {

            @Override
            public void call(Subscriber<? super PoolStateChangeEvent> t1) {
                stats.getPublishSubject().subscribe(t1);
            }
        });
    }
}
