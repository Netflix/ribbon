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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

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
public class DynamicServerListLoadBalancer<T extends Server> extends
        BaseLoadBalancer {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(DynamicServerListLoadBalancer.class);

    boolean isSecure = false;
    boolean useTunnel = false;
    private Thread _shutdownThread;

    // to keep track of modification of server lists
    protected AtomicBoolean serverListUpdateInProgress = new AtomicBoolean(
            false);

    private static long LISTOFSERVERS_CACHE_UPDATE_DELAY = 1000; // msecs;
    private static long LISTOFSERVERS_CACHE_REPEAT_INTERVAL = 30 * 1000; // msecs;
                                                                         // //
                                                                         // every
                                                                         // 30
                                                                         // secs

    private ScheduledThreadPoolExecutor _serverListRefreshExecutor = null;

    private long refeshIntervalMills = LISTOFSERVERS_CACHE_REPEAT_INTERVAL;

    volatile ServerList<T> serverListImpl;

    volatile ServerListFilter<T> filter;

    public DynamicServerListLoadBalancer() {
        super();
    }

    public DynamicServerListLoadBalancer(IClientConfig niwsClientConfig) {
        initWithNiwsConfig(niwsClientConfig);
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

            refeshIntervalMills = Integer.valueOf(clientConfig.getProperty(
                    CommonClientConfigKey.ServerListRefreshInterval,
                    LISTOFSERVERS_CACHE_REPEAT_INTERVAL).toString());

            boolean primeConnection = this.isEnablePrimingConnections();
            // turn this off to avoid duplicated asynchronous priming done in BaseLoadBalancer.setServerList()
            this.setEnablePrimingConnections(false);
            enableAndInitLearnNewServersFeature();

            updateListOfServers();
            if (primeConnection && this.getPrimeConnections() != null) {
                this.getPrimeConnections()
                        .primeConnections(getServerList(true));
            }
            this.setEnablePrimingConnections(primeConnection);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Exception while initializing NIWSDiscoveryLoadBalancer:"
                            + clientConfig.getClientName()
                            + ", niwsClientConfig:" + clientConfig, e);
        }
    }

    @Override
    public void setServersList(List lsrv) {
        super.setServersList(lsrv);
        List<T> serverList = (List<T>) lsrv;
        Map<String, List<Server>> serversInZones = new HashMap<String, List<Server>>();
        for (Server server : serverList) {
            // make sure ServerStats is created to avoid creating them on hot
            // path
            getLoadBalancerStats().getSingleServerStat(server);
            String zone = server.getZone();
            if (zone != null) {
                zone = zone.toLowerCase();
                List<Server> servers = serversInZones.get(zone);
                if (servers == null) {
                    servers = new ArrayList<Server>();
                    serversInZones.put(zone, servers);
                }
                servers.add(server);
            }
        }
        setServerListForZones(serversInZones);
    }

    protected void setServerListForZones(
            Map<String, List<Server>> zoneServersMap) {
        LOGGER.debug("Setting server list for zones: {}", zoneServersMap);
        getLoadBalancerStats().updateZoneServerMapping(zoneServersMap);
    }

    public ServerList<T> getServerListImpl() {
        return serverListImpl;
    }

    public void setServerListImpl(ServerList<T> niwsServerList) {
        this.serverListImpl = niwsServerList;
    }

    @Override
    public void setPing(IPing ping) {
        this.ping = ping;
    }

    public ServerListFilter<T> getFilter() {
        return filter;
    }

    public void setFilter(ServerListFilter<T> filter) {
        this.filter = filter;
    }

    @Override
    /**
     * Makes no sense to ping an inmemory disc client
     * 
     */
    public void forceQuickPing() {
        // no-op
    }

    /**
     * Feature that lets us add new instances (from AMIs) to the list of
     * existing servers that the LB will use Call this method if you want this
     * feature enabled
     */
    public void enableAndInitLearnNewServersFeature() {
        String threadName = "DynamicServerListLoadBalancer-" + getIdentifier();
        ThreadFactory factory = (new ThreadFactoryBuilder()).setDaemon(true)
                .setNameFormat(threadName).build();
        _serverListRefreshExecutor = new ScheduledThreadPoolExecutor(1, factory);
        keepServerListUpdated();

        // Add it to the shutdown hook

        if (_shutdownThread == null) {

            _shutdownThread = new Thread(new Runnable() {
                public void run() {
                    LOGGER.info("Shutting down the Executor Pool for "
                            + getIdentifier());
                    shutdownExecutorPool();
                }
            });

            Runtime.getRuntime().addShutdownHook(_shutdownThread);
        }
    }

    private String getIdentifier() {
        return this.getClientConfig().getClientName();
    }

    private void keepServerListUpdated() {
        _serverListRefreshExecutor.scheduleAtFixedRate(
                new ServerListRefreshExecutorThread(),
                LISTOFSERVERS_CACHE_UPDATE_DELAY, refeshIntervalMills,
                TimeUnit.MILLISECONDS);
    }

    public void shutdownExecutorPool() {
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

    @VisibleForTesting
    public void updateListOfServers() {
        List<T> servers = new ArrayList<T>();
        if (serverListImpl != null) {
            servers = serverListImpl.getUpdatedListOfServers();
            LOGGER.debug("List of Servers obtained from Discovery client:"
                    + servers);

            if (filter != null) {
                servers = filter.getFilteredListOfServers(servers);
                LOGGER.debug("Filtered List of Servers obtained from Discovery client:"
                        + servers);
            }
        }
        updateAllServerList(servers);
    }

    /**
     * Update the AllServer list in the LoadBalancer if necessary and enabled
     * 
     * @param ls
     */
    protected void updateAllServerList(List<T> ls) {
        // other threads might be doing this - in which case, we pass
        if (!serverListUpdateInProgress.get()) {
            serverListUpdateInProgress.set(true);
            for (T s : ls) {
                s.setAlive(true); // set so that clients can start using these
                                  // servers right away instead
                // of having to wait out the ping cycle.
            }
            setServersList(ls);
            super.forceQuickPing();
            serverListUpdateInProgress.set(false);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("DynamicServerListLoadBalancer:");
        sb.append(super.toString());
        sb.append("ServerList:" + String.valueOf(serverListImpl));
        return sb.toString();
    }
}
