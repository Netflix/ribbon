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

        private static final Thread SHUTDOWN_THREAD = new Thread(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("Shutting down the Executor for EurekaDynamicNotificationLoadBalancer");
                try {
                    DEFAULT_SERVER_LIST_UPDATE_EXECUTOR.shutdown();
                    Runtime.getRuntime().removeShutdownHook(SHUTDOWN_THREAD);
                } catch (Exception e) {
                    // this can happen in the middle of a real shutdown, and that's ok.
                }

            }
        });

        static {
            Runtime.getRuntime().addShutdownHook(SHUTDOWN_THREAD);
        }
    }

    public static ExecutorService getDefaultServerListUpdateExecutor() {
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
    // callers of this must ensure initWithNiwsConfig is called also
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
