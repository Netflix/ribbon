package com.netflix.loadbalancer;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.client.config.IClientConfig;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractDynamicServerListLoadBalancer<T extends Server> extends BaseLoadBalancer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDynamicServerListLoadBalancer.class);

    // to keep track of modification of server lists
    protected AtomicBoolean serverListUpdateInProgress = new AtomicBoolean(false);

    protected volatile ServerList<T> serverListImpl;

    protected volatile ServerListFilter<T> filter;

    protected AtomicLong lastUpdated = new AtomicLong(System.currentTimeMillis());


    protected AbstractDynamicServerListLoadBalancer() {
        super();
    }

    protected AbstractDynamicServerListLoadBalancer(IClientConfig clientConfig, IRule rule, IPing ping) {
        super(clientConfig, rule, ping);
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

    protected void setServerListForZones(Map<String, List<Server>> zoneServersMap) {
        LOGGER.debug("Setting server list for zones: {}", zoneServersMap);
        getLoadBalancerStats().updateZoneServerMapping(zoneServersMap);
    }

    public ServerList<T> getServerListImpl() {
        return serverListImpl;
    }

    public void setServerListImpl(ServerList<T> niwsServerList) {
        this.serverListImpl = niwsServerList;
    }

    public ServerListFilter<T> getFilter() {
        return filter;
    }

    public void setFilter(ServerListFilter<T> filter) {
        this.filter = filter;
    }

    private String getIdentifier() {
        return this.getClientConfig().getClientName();
    }

    public abstract void stopServerListRefreshing();

    @VisibleForTesting
    public void updateListOfServers() {
        List<T> servers = new ArrayList<T>();
        if (serverListImpl != null) {
            servers = serverListImpl.getUpdatedListOfServers();
            LOGGER.debug("List of Servers for {} obtained from Discovery client: {}",
                    getIdentifier(), servers);

            if (filter != null) {
                servers = filter.getFilteredListOfServers(servers);
                LOGGER.debug("Filtered List of Servers for {} obtained from Discovery client: {}",
                        getIdentifier(), servers);
            }
        }
        lastUpdated.set(System.currentTimeMillis());
        updateAllServerList(servers);
    }

    /**
     * Update the AllServer list in the LoadBalancer if necessary and enabled
     *
     * @param ls
     */
    protected void updateAllServerList(List<T> ls) {
        // other threads might be doing this - in which case, we pass
        if (serverListUpdateInProgress.compareAndSet(false, true)) {
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

    @Monitor(name="LastUpdated", type=DataSourceType.INFORMATIONAL)
    public String getLastUpdate() {
        return new Date(lastUpdated.get()).toString();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        stopServerListRefreshing();
    }
}
