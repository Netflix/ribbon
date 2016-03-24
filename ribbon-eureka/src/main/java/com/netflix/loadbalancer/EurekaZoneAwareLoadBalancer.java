package com.netflix.loadbalancer;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicDoubleProperty;
import com.netflix.discovery.EurekaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * An implementation of the {@link com.netflix.loadbalancer.ZoneAwareLoadBalancer} specific for the
 * {@link com.netflix.loadbalancer.EurekaDynamicNotificationLoadBalancer} base implementation.
 *
 * Note that the dynamic properties for this LB and the {@link com.netflix.loadbalancer.ZoneAwareLoadBalancer}
 * share the same configuration at {@link com.netflix.loadbalancer.ZoneAwareLoadBalancerConfig}
 * as they have equivalent semantic meaning.
 *
 * Why not re-use? The current interface structures and implementations makes is quite difficult to
 * decompose while maintaining good backwards compatibility.
 *
 * @author David Liu
 */
public class EurekaZoneAwareLoadBalancer extends EurekaDynamicNotificationLoadBalancer {

    private ConcurrentHashMap<String, BaseLoadBalancer> balancers = new ConcurrentHashMap<String, BaseLoadBalancer>();

    private static final Logger logger = LoggerFactory.getLogger(EurekaZoneAwareLoadBalancer.class);

    private volatile DynamicDoubleProperty triggeringLoad;

    private volatile DynamicDoubleProperty triggeringBlackoutPercentage;

    void setUpServerList(List<Server> upServerList) {
        this.upServerList = upServerList;
    }

    public EurekaZoneAwareLoadBalancer() {
        super();
    }

    public EurekaZoneAwareLoadBalancer(IClientConfig niwsClientConfig,
                                       Provider<EurekaClient> eurekaClientProvider,
                                       ExecutorService serverListUpdateService) {
        super(niwsClientConfig, eurekaClientProvider, serverListUpdateService);
    }

    @Override
    protected void setServerListForZones(Map<String, List<Server>> zoneServersMap) {
        super.setServerListForZones(zoneServersMap);
        if (balancers == null) {
            balancers = new ConcurrentHashMap<String, BaseLoadBalancer>();
        }
        for (Map.Entry<String, List<Server>> entry: zoneServersMap.entrySet()) {
            String zone = entry.getKey().toLowerCase();
            getLoadBalancer(zone).setServersList(entry.getValue());
        }
        // check if there is any zone that no longer has a server
        // and set the list to empty so that the zone related metrics does not
        // contain stale data
        for (Map.Entry<String, BaseLoadBalancer> existingLBEntry: balancers.entrySet()) {
            if (!zoneServersMap.keySet().contains(existingLBEntry.getKey())) {
                existingLBEntry.getValue().setServersList(Collections.emptyList());
            }
        }
    }

    @Override
    public Server chooseServer(Object key) {
        if (!ZoneAwareLoadBalancerConfig.getEnabled() || getLoadBalancerStats().getAvailableZones().size() <= 1) {
            logger.debug("Zone aware logic disabled or there is only one zone");
            return super.chooseServer(key);
        }
        Server server = null;
        try {
            LoadBalancerStats lbStats = getLoadBalancerStats();
            Map<String, ZoneSnapshot> zoneSnapshot = ZoneAvoidanceRule.createSnapshot(lbStats);
            logger.debug("Zone snapshots: {}", zoneSnapshot);
            if (triggeringLoad == null) {
                triggeringLoad = ZoneAwareLoadBalancerConfig.newTriggeringLoadProperty(this.getName());
            }

            if (triggeringBlackoutPercentage == null) {
                triggeringBlackoutPercentage = ZoneAwareLoadBalancerConfig.newTriggeringBlackoutPercentageProperty(this.getName());
            }

            Set<String> availableZones = ZoneAvoidanceRule.getAvailableZones(zoneSnapshot, triggeringLoad.get(), triggeringBlackoutPercentage.get());
            logger.debug("Available zones: {}", availableZones);
            if (availableZones != null &&  availableZones.size() < zoneSnapshot.keySet().size()) {
                String zone = ZoneAvoidanceRule.randomChooseZone(zoneSnapshot, availableZones);
                logger.debug("Zone chosen: {}", zone);
                if (zone != null) {
                    BaseLoadBalancer zoneLoadBalancer = getLoadBalancer(zone);
                    server = zoneLoadBalancer.chooseServer(key);
                }
            }
        } catch (Throwable e) {
            logger.error("Unexpected exception when choosing server using zone aware logic", e);
        }
        if (server != null) {
            return server;
        } else {
            logger.debug("Zone avoidance logic is not invoked.");
            return super.chooseServer(key);
        }
    }

    @VisibleForTesting
    BaseLoadBalancer getLoadBalancer(String zone) {
        zone = zone.toLowerCase();
        BaseLoadBalancer loadBalancer = balancers.get(zone);
        if (loadBalancer == null) {
            // We need to create rule object for load balancer for each zone
            IRule rule = cloneRule(this.getRule());
            loadBalancer = new BaseLoadBalancer(this.getName() + "_" + zone, rule, this.getLoadBalancerStats());
            BaseLoadBalancer prev = balancers.putIfAbsent(zone, loadBalancer);
            if (prev != null) {
                loadBalancer = prev;
            }
        }
        return loadBalancer;
    }

    private IRule cloneRule(IRule toClone) {
        IRule rule;
        if (toClone == null) {
            rule = new AvailabilityFilteringRule();
        } else {
            String ruleClass = toClone.getClass().getName();
            try {
                rule = (IRule) ClientFactory.instantiateInstanceWithClientConfig(ruleClass, this.getClientConfig());
            } catch (Exception e) {
                throw new RuntimeException("Unexpected exception creating rule for ZoneAwareEurekaLoadBalancer", e);
            }
        }
        return rule;
    }


    @Override
    public void setRule(IRule rule) {
        super.setRule(rule);
        if (balancers != null) {
            for (String zone: balancers.keySet()) {
                balancers.get(zone).setRule(cloneRule(rule));
            }
        }
    }
}
