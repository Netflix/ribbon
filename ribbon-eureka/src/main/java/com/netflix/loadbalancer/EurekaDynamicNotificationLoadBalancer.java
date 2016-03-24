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

    public static final ExecutorService DEFAULT_SERVER_LIST_UPDATE_EXECUTOR = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                    .setNameFormat("EurekaDynamicNotificationLoadBalancer-ServerListUpdate-%d")
                    .setDaemon(true)
                    .build()
    );

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
                DEFAULT_SERVER_LIST_UPDATE_EXECUTOR
        );
    }

    public EurekaDynamicNotificationLoadBalancer(IClientConfig clientConfig,
                                                 Provider<EurekaClient> eurekaClientProvider,
                                                 ExecutorService serverListUpdateService) {
        super();

        this.eurekaClientProvider = eurekaClientProvider;
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

        serverListUpdateService.shutdown();
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
    private void connectToEureka() {
        updateInitialServerList();
        if (eurekaClientProvider != null) {
            EurekaClient eurekaClient = eurekaClientProvider.get();
            if (eurekaClient != null) {
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
