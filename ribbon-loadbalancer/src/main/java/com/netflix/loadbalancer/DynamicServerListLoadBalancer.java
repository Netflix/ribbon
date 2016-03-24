/*
 *
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.loadbalancer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicIntProperty;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A LoadBalancer that has the capabilities to obtain the candidate list of
 * servers using a dynamic source. i.e. The list of servers can potentially be
 * changed at Runtime. It also contains facilities wherein the list of servers
 * can be passed through a Filter criteria to filter out servers that do not
 * meet the desired criteria.
 * 
 * @author stonse
 * 
 */
public class DynamicServerListLoadBalancer<T extends Server> extends AbstractDynamicServerListLoadBalancer<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicServerListLoadBalancer.class);

    boolean isSecure = false;
    boolean useTunnel = false;
    private static Thread _shutdownThread;

    private static long LISTOFSERVERS_CACHE_UPDATE_DELAY = 1000; // msecs;
    private static int LISTOFSERVERS_CACHE_REPEAT_INTERVAL = 30 * 1000; // msecs;

    private static ScheduledThreadPoolExecutor _serverListRefreshExecutor = null;

    private long refeshIntervalMills = LISTOFSERVERS_CACHE_REPEAT_INTERVAL;

    protected volatile boolean serverRefreshEnabled = false;
    private final static String CORE_THREAD = "DynamicServerListLoadBalancer.ThreadPoolSize";
    private final static DynamicIntProperty poolSizeProp = new DynamicIntProperty(CORE_THREAD, 2);
    
    private volatile ScheduledFuture<?> scheduledFuture;

    static {
        int coreSize = poolSizeProp.get();
        ThreadFactory factory = new ThreadFactoryBuilder()
                .setNameFormat("DynamicServerListLoadBalancer-ServerListUpdate-%d")
                .setDaemon(true)
                .build();
        _serverListRefreshExecutor = new ScheduledThreadPoolExecutor(coreSize, factory);
        poolSizeProp.addCallback(new Runnable() {
            @Override
            public void run() {
                _serverListRefreshExecutor.setCorePoolSize(poolSizeProp.get());
            }
        
        });
        _shutdownThread = new Thread(new Runnable() {
            public void run() {
                LOGGER.info("Shutting down the Executor Pool for DynamicServerListLoadBalancer");
                shutdownExecutorPool();
            }
        });
        Runtime.getRuntime().addShutdownHook(_shutdownThread);
    }
    
    public DynamicServerListLoadBalancer() {
        super();
    }

    public DynamicServerListLoadBalancer(IClientConfig clientConfig, IRule rule, IPing ping, 
            ServerList<T> serverList, ServerListFilter<T> filter) {
        super(clientConfig, rule, ping);
        this.serverListImpl = serverList;
        this.filter = filter;
        if (filter instanceof AbstractServerListFilter) {
            ((AbstractServerListFilter) filter).setLoadBalancerStats(getLoadBalancerStats());
        }
        restOfInit(clientConfig);
    }

    public DynamicServerListLoadBalancer(IClientConfig clientConfig) {
        initWithNiwsConfig(clientConfig);
    }
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        try {
            super.initWithNiwsConfig(clientConfig);
            String niwsServerListClassName = clientConfig.getProperty(
                    CommonClientConfigKey.NIWSServerListClassName,
                    DefaultClientConfigImpl.DEFAULT_SEVER_LIST_CLASS)
                    .toString();

            ServerList<T> niwsServerListImpl = (ServerList<T>) ClientFactory
                    .instantiateInstanceWithClientConfig(
                            niwsServerListClassName, clientConfig);
            this.serverListImpl = niwsServerListImpl;

            if (niwsServerListImpl instanceof AbstractServerList) {
                AbstractServerListFilter<T> niwsFilter = ((AbstractServerList) niwsServerListImpl)
                        .getFilterImpl(clientConfig);
                niwsFilter.setLoadBalancerStats(getLoadBalancerStats());
                this.filter = niwsFilter;
            }

            restOfInit(clientConfig);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Exception while initializing NIWSDiscoveryLoadBalancer:"
                            + clientConfig.getClientName()
                            + ", niwsClientConfig:" + clientConfig, e);
        }
    }

    void restOfInit(IClientConfig clientConfig) {
        refeshIntervalMills =clientConfig.get(CommonClientConfigKey.ServerListRefreshInterval, LISTOFSERVERS_CACHE_REPEAT_INTERVAL);

        boolean primeConnection = this.isEnablePrimingConnections();
        // turn this off to avoid duplicated asynchronous priming done in BaseLoadBalancer.setServerList()
        this.setEnablePrimingConnections(false);
        enableAndInitLearnNewServersFeature();

        updateListOfServers();
        if (primeConnection && this.getPrimeConnections() != null) {
            this.getPrimeConnections()
                    .primeConnections(getReachableServers());
        }
        this.setEnablePrimingConnections(primeConnection);
        LOGGER.info("DynamicServerListLoadBalancer for client {} initialized: {}", clientConfig.getClientName(), this.toString());
    }
    
    /**
     * Makes no sense to ping an inmemory disc client
     */
    @Override
    public void forceQuickPing() {
        // no-op
    }

    /**
     * Feature that lets us add new instances (from AMIs) to the list of
     * existing servers that the LB will use Call this method if you want this
     * feature enabled
     */
    public void enableAndInitLearnNewServersFeature() {
        keepServerListUpdated();
        serverRefreshEnabled = true;
    }

    private void keepServerListUpdated() {
        scheduledFuture = _serverListRefreshExecutor.scheduleAtFixedRate(
                new ServerListRefreshExecutorThread(),
                LISTOFSERVERS_CACHE_UPDATE_DELAY, refeshIntervalMills,
                TimeUnit.MILLISECONDS);
    }

    private static void shutdownExecutorPool() {
        if (_serverListRefreshExecutor != null) {
            _serverListRefreshExecutor.shutdown();

            if (_shutdownThread != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(_shutdownThread);
                } catch (IllegalStateException ise) { // NOPMD
                    // this can happen if we're in the middle of a real
                    // shutdown,
                    // and that's 'ok'
                }
            }

        }
    }

    @Override
    public void stopServerListRefreshing() {
        serverRefreshEnabled = false;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }
    
    /**
     * Class that updates the list of Servers This is based on the method used
     * by the client * Appropriate Filters are applied before coming up with the
     * right set of servers
     * 
     * @author stonse
     * 
     */
    class ServerListRefreshExecutorThread implements Runnable {

        public void run() {
            if (!serverRefreshEnabled) {
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(true);
                }
                return;
            }
            try {
                updateListOfServers();

            } catch (Throwable e) {
                LOGGER.error(
                        "Exception while updating List of Servers obtained from Discovery client",
                        e);
                // e.printStackTrace();
            }
        }

    }

    @Monitor(name="NumUpdateCyclesMissed", type=DataSourceType.GAUGE)
    public int getNumberMissedCycles() {
        if (!serverRefreshEnabled) {
            return 0;
        }
        return (int) ((int) (System.currentTimeMillis() - lastUpdated.get()) / refeshIntervalMills);
    }
    
    @Monitor(name="NumThreads", type=DataSourceType.GAUGE)
    public int getCoreThreads() {
        if (_serverListRefreshExecutor != null) {
            return _serverListRefreshExecutor.getCorePoolSize();
        } else {
            return 0;
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DynamicServerListLoadBalancer:");
        sb.append(super.toString());
        sb.append("ServerList:" + String.valueOf(serverListImpl));
        return sb.toString();
    }
}
