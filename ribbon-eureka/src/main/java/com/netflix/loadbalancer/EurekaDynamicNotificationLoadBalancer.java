package com.netflix.loadbalancer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.client.config.IClientConfig;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaEvent;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A eureka specific dynamic loadbalancer that can update it's internal server list via notifications
 * of cache updates from {@link com.netflix.discovery.EurekaClient}.
 *
 * Note that when a cache refreshed notification is received, the actual update on the serverList is
 * done on a separate scheduler as the notification is delivered on an eurekaClient thread.
 *
 * @author David Liu
 */
public class EurekaDynamicNotificationLoadBalancer extends AbstractDynamicServerListLoadBalancer<DiscoveryEnabledServer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EurekaDynamicNotificationLoadBalancer.class);

    private static class LazyHolder {
        private static final ExecutorService DEFAULT_SERVER_LIST_UPDATE_EXECUTOR = Executors.newCachedThreadPool(
                new ThreadFactoryBuilder()
                        .setNameFormat("EurekaDynamicNotificationLoadBalancer-ServerListUpdate-%d")
                        .setDaemon(true)
                        .build()
        );
    }

    private static final AtomicInteger DEFAULT_EXECUTOR_REFERENCES = new AtomicInteger(0);

    public static ExecutorService getDefaultServerListUpdateExecutor() {
        DEFAULT_EXECUTOR_REFERENCES.incrementAndGet();
        return LazyHolder.DEFAULT_SERVER_LIST_UPDATE_EXECUTOR;
    }

    private final EurekaEventListener updateListener = new EurekaEventListener() {
        @Override
        public void onEvent(EurekaEvent event) {
            if (event instanceof CacheRefreshedEvent) {
                serverListUpdateService.submit(new Runnable() {  // fire and forget
                    @Override
                    public void run() {
                        EurekaDynamicNotificationLoadBalancer.super.updateListOfServers();
                    }
                });
            }
        }
    };

    private final Provider<EurekaClient> eurekaClientProvider;
    private final ExecutorService serverListUpdateService;

    // necessary for legacy use cases :(
    @Deprecated
    public EurekaDynamicNotificationLoadBalancer() {
        this(
                null,
                new LegacyEurekaClientProvider(),
                getDefaultServerListUpdateExecutor()
        );
    }

    public EurekaDynamicNotificationLoadBalancer(final IClientConfig clientConfig,
                                                 final Provider<EurekaClient> eurekaClientProvider,
                                                 ExecutorService serverListUpdateService) {
        super();

        // wrap around a singleton provider as provider interface does not require this
        this.eurekaClientProvider = new Provider<EurekaClient>() {
            private EurekaClient clientInstance;
            @Override
            public synchronized EurekaClient get() {
                if (clientInstance == null) {
                    clientInstance = eurekaClientProvider.get();
                }
                return clientInstance;
            }
        };

        this.serverListUpdateService = serverListUpdateService;

        if (clientConfig != null) {
            initWithNiwsConfig(clientConfig);
        }
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        try {
            super.initWithNiwsConfig(clientConfig);

            DiscoveryEnabledNIWSServerList myServerList = new DiscoveryEnabledNIWSServerList(
                    clientConfig,
                    eurekaClientProvider
            );
            setServerListImpl(myServerList);

            AbstractServerListFilter<DiscoveryEnabledServer> myFilter = myServerList.getFilterImpl(clientConfig);
            myFilter.setLoadBalancerStats(getLoadBalancerStats());
            setFilter(myFilter);

            connectToEureka();

            LOGGER.info("EurekaDynamicNotificationLoadBalancer for client {} initialized: {}",
                    clientConfig.getClientName(), this.toString());
        } catch (Exception e) {
            throw new RuntimeException("Exception while initializing EurekaDynamicNotificationLoadBalancer," +
                    " niwsClientConfig: " + clientConfig, e);
        }
    }

    /**
     * Makes no sense to ping an inmemory disc client
     */
    @Override
    public void forceQuickPing() {
        // no-op
    }

    @Override
    public void stopServerListRefreshing() {
        if (eurekaClientProvider != null) {
            EurekaClient eurekaClient = eurekaClientProvider.get();
            if (eurekaClient != null) {
                eurekaClient.unregisterEventListener(updateListener);
            }
        }

        if (DEFAULT_EXECUTOR_REFERENCES.get() > 0) {  // a reference exist so can access the lazy holder
            if (serverListUpdateService == LazyHolder.DEFAULT_SERVER_LIST_UPDATE_EXECUTOR) {  // is the default reference
                if (DEFAULT_EXECUTOR_REFERENCES.decrementAndGet() <= 0) {
                    LOGGER.info("Shutting down the default serverListUpdateExecutor as this is the last reference");
                    serverListUpdateService.shutdown();
                    return;
                } else {
                    LOGGER.info("Not shutting down the default serverListUpdateExecutor as there are {}" +
                            " other references to it", DEFAULT_EXECUTOR_REFERENCES.get());
                    return;
                }
            }
        }

        LOGGER.info("The current serverListUpdateExecutor is custom and is provided by a caller: {} " +
                "Expecting the caller to shutdown the executor at the correct time.", serverListUpdateService);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("EurekaDynamicNotificationLoadBalancer:");
        sb.append(super.toString());
        sb.append("ServerList:" + String.valueOf(serverListImpl));
        return sb.toString();
    }

    @Monitor(name="DurationSinceLastUpdateMs", type= DataSourceType.GAUGE)
    public long getDurationSinceLastUpdateMs() {
        return System.currentTimeMillis() - lastUpdated.get();
    }

    @Monitor(name="DefaultServerListExecutorReferences", type= DataSourceType.GAUGE)
    public int getDefaultServerListExecutorReferences() {
        return DEFAULT_EXECUTOR_REFERENCES.get();
    }

    // idempotent
    private void connectToEureka() throws IllegalStateException {
        updateInitialServerList();
        if (eurekaClientProvider != null) {
            EurekaClient eurekaClient = eurekaClientProvider.get();
            if (eurekaClient == null) {
                throw new IllegalStateException("Cannot connect to eureka as the client provided is null");
            } else {
                eurekaClient.registerEventListener(updateListener);
            }
        }
    }

    private void updateInitialServerList() {
        boolean primeConnection = this.isEnablePrimingConnections();
        // turn this off to avoid duplicated asynchronous priming done in BaseLoadBalancer.setServerList()
        this.setEnablePrimingConnections(false);

        super.updateListOfServers();

        if (primeConnection && this.getPrimeConnections() != null) {
            this.getPrimeConnections().primeConnections(getReachableServers());
        }
        this.setEnablePrimingConnections(primeConnection);
    }
}
