package com.netflix.client.netty;

import java.util.concurrent.ScheduledExecutorService;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.config.IClientConfigKey.CommonKeys;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;

import io.reactivex.netty.client.CompositePoolLimitDeterminationStrategy;
import io.reactivex.netty.client.MaxConnectionsBasedStrategy;
import io.reactivex.netty.client.PoolLimitDeterminationStrategy;
import io.reactivex.netty.client.PoolStats;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import rx.Observable;
import rx.Subscriber;
import rx.Observable.OnSubscribe;

public abstract class LoadBalancingRxClientWithPoolOptions<I, O, T extends RxClient<I, O>> extends LoadBalancingRxClient<I, O, T>{
    protected CompositePoolLimitDeterminationStrategy poolStrategy;
    protected MaxConnectionsBasedStrategy globalStrategy;
    protected int idleConnectionEvictionMills;
    protected GlobalPoolStats<T> stats;
    private Observable<PoolStateChangeEvent> poolStateChangeEventObservable; 
    protected ScheduledExecutorService poolCleanerScheduler;
    protected boolean poolEnabled;

    public LoadBalancingRxClientWithPoolOptions(IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<O, I> pipelineConfigurator, ScheduledExecutorService poolCleanerScheduler) {
        this(LoadBalancerBuilder.newBuilder().withClientConfig(config).buildDynamicServerListLoadBalancer(),
                config,
                retryHandler,
                pipelineConfigurator,
                poolCleanerScheduler);
    }

    public LoadBalancingRxClientWithPoolOptions(ILoadBalancer lb, IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<O, I> pipelineConfigurator, ScheduledExecutorService poolCleanerScheduler) {
        super(lb, config, retryHandler, pipelineConfigurator);
        poolEnabled = config.getPropertyWithType(CommonClientConfigKey.EnableConnectionPool, 
                DefaultClientConfigImpl.DEFAULT_ENABLE_CONNECTION_POOL);
        if (poolEnabled) {
            this.poolCleanerScheduler = poolCleanerScheduler;
            int maxTotalConnections = config.getPropertyWithType(IClientConfigKey.CommonKeys.MaxTotalConnections,
                    DefaultClientConfigImpl.DEFAULT_MAX_TOTAL_CONNECTIONS);
            int maxConnections = config.getPropertyWithType(CommonKeys.MaxConnectionsPerHost, DefaultClientConfigImpl.DEFAULT_MAX_CONNECTIONS_PER_HOST);
            MaxConnectionsBasedStrategy perHostStrategy = new DynamicPropertyBasedPoolStrategy(maxConnections,
                    config.getClientName() + "." + config.getNameSpace() + "." + CommonClientConfigKey.MaxConnectionsPerHost);
            globalStrategy = new DynamicPropertyBasedPoolStrategy(maxTotalConnections, 
                    config.getClientName() + "." + config.getNameSpace() + "." + CommonClientConfigKey.MaxTotalConnections);
            poolStrategy = new CompositePoolLimitDeterminationStrategy(perHostStrategy, globalStrategy);
            idleConnectionEvictionMills = config.getPropertyWithType(CommonKeys.ConnIdleEvictTimeMilliSeconds, DefaultClientConfigImpl.DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS);
            stats = new GlobalPoolStats<T>(config.getClientName(), globalStrategy, rxClientCache);
            poolStateChangeEventObservable = Observable.create(new OnSubscribe<PoolStateChangeEvent>() {
                @Override
                public void call(Subscriber<? super PoolStateChangeEvent> t1) {
                    stats.getPublishSubject().subscribe(t1);
                }
            });
        }
    }

    protected final PoolLimitDeterminationStrategy getPoolStrategy() {
        return globalStrategy;
    }
    
    protected int getConnectionIdleTimeoutMillis() {
        return idleConnectionEvictionMills;
    }
    
    protected boolean isPoolEnabled() {
        return poolEnabled;
    }
    
    @Override
    public Observable<PoolStateChangeEvent> poolStateChangeObservable() {
        if (poolEnabled) {
            return poolStateChangeEventObservable;
        } else {
            return Observable.empty();
        }
    }

    @Override
    public PoolStats getStats() {
        return stats;
    }
}

