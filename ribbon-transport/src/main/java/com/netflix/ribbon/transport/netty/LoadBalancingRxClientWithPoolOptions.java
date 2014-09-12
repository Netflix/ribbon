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

import io.reactivex.netty.client.CompositePoolLimitDeterminationStrategy;
import io.reactivex.netty.client.MaxConnectionsBasedStrategy;
import io.reactivex.netty.client.PoolLimitDeterminationStrategy;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;

import java.util.concurrent.ScheduledExecutorService;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.config.IClientConfigKey.Keys;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;

public abstract class LoadBalancingRxClientWithPoolOptions<I, O, T extends RxClient<I, O>> extends LoadBalancingRxClient<I, O, T>{
    protected CompositePoolLimitDeterminationStrategy poolStrategy;
    protected MaxConnectionsBasedStrategy globalStrategy;
    protected int idleConnectionEvictionMills;
    protected ScheduledExecutorService poolCleanerScheduler;
    protected boolean poolEnabled = true;

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
        poolEnabled = config.get(CommonClientConfigKey.EnableConnectionPool, 
                DefaultClientConfigImpl.DEFAULT_ENABLE_CONNECTION_POOL);
        if (poolEnabled) {
            this.poolCleanerScheduler = poolCleanerScheduler;
            int maxTotalConnections = config.get(IClientConfigKey.Keys.MaxTotalConnections,
                    DefaultClientConfigImpl.DEFAULT_MAX_TOTAL_CONNECTIONS);
            int maxConnections = config.get(Keys.MaxConnectionsPerHost, DefaultClientConfigImpl.DEFAULT_MAX_CONNECTIONS_PER_HOST);
            MaxConnectionsBasedStrategy perHostStrategy = new DynamicPropertyBasedPoolStrategy(maxConnections,
                    config.getClientName() + "." + config.getNameSpace() + "." + CommonClientConfigKey.MaxConnectionsPerHost);
            globalStrategy = new DynamicPropertyBasedPoolStrategy(maxTotalConnections, 
                    config.getClientName() + "." + config.getNameSpace() + "." + CommonClientConfigKey.MaxTotalConnections);
            poolStrategy = new CompositePoolLimitDeterminationStrategy(perHostStrategy, globalStrategy);
            idleConnectionEvictionMills = config.get(Keys.ConnIdleEvictTimeMilliSeconds, DefaultClientConfigImpl.DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS);
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
    public int getMaxConcurrentRequests() {
        if (poolEnabled) {
            return globalStrategy.getMaxConnections();
        }
        return super.getMaxConcurrentRequests();
    }
}
