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

import com.netflix.client.config.DynamicProperty;
import com.netflix.client.config.DynamicPropertyRepository;
import com.netflix.client.config.IClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * A server predicate that filters out all servers in a worst zone if the aggregated metric for that zone reaches a threshold.
 * The logic to determine the worst zone is described in class {@link ZoneAwareLoadBalancer}.  
 * 
 * @author awang
 *
 */
public class ZoneAvoidancePredicate extends AbstractServerPredicate {

    private static final Logger logger = LoggerFactory.getLogger(ZoneAvoidancePredicate.class);
    private final DynamicPropertyRepository repository;

    private DynamicProperty<Double> triggeringLoad;

    private DynamicProperty<Double> triggeringBlackoutPercentage;
    
    private DynamicProperty<Boolean> enabled;

    public ZoneAvoidancePredicate(IRule rule, IClientConfig clientConfig) {
        super(rule);
        this.repository = clientConfig == null ? DynamicPropertyRepository.DEFAULT : clientConfig.getDynamicPropertyRepository();
        initDynamicProperties(clientConfig);
    }

    public ZoneAvoidancePredicate(LoadBalancerStats lbStats, IClientConfig clientConfig) {
        super(lbStats);
        this.repository = clientConfig == null ? DynamicPropertyRepository.DEFAULT : clientConfig.getDynamicPropertyRepository();
        initDynamicProperties(clientConfig);
    }

    ZoneAvoidancePredicate(IRule rule) {
        this(rule, null);
    }
    
    private void initDynamicProperties(IClientConfig clientConfig) {
        this.enabled = repository.getProperty("niws.loadbalancer.zoneAvoidanceRule.enabled", Boolean.class, true);

        if (clientConfig != null) {
            triggeringLoad = repository.getProperty(
                    "ZoneAwareNIWSDiscoveryLoadBalancer." + clientConfig.getClientName() + ".triggeringLoadPerServerThreshold",
                    Double.class,
                    0.2d);

            triggeringBlackoutPercentage = repository.getProperty(
                    "ZoneAwareNIWSDiscoveryLoadBalancer." + clientConfig.getClientName() + ".avoidZoneWithBlackoutPercetage",
                    Double.class,
                    0.99999d);
        } else {
            triggeringLoad = repository.getProperty("ZoneAwareNIWSDiscoveryLoadBalancer.triggeringLoadPerServerThreshold", Double.class, 0.2d);

            triggeringBlackoutPercentage = repository.getProperty("ZoneAwareNIWSDiscoveryLoadBalancer.avoidZoneWithBlackoutPercetage", Double.class, 0.99999d);
        }
        
    }

    @Override
    public boolean apply(@Nullable PredicateKey input) {
        if (!enabled.get()) {
            return true;
        }
        String serverZone = input.getServer().getZone();
        if (serverZone == null) {
            // there is no zone information from the server, we do not want to filter
            // out this server
            return true;
        }
        LoadBalancerStats lbStats = getLBStats();
        if (lbStats == null) {
            // no stats available, do not filter
            return true;
        }
        if (lbStats.getAvailableZones().size() <= 1) {
            // only one zone is available, do not filter
            return true;
        }
        Map<String, ZoneSnapshot> zoneSnapshot = ZoneAvoidanceRule.createSnapshot(lbStats);
        if (!zoneSnapshot.keySet().contains(serverZone)) {
            // The server zone is unknown to the load balancer, do not filter it out 
            return true;
        }
        logger.debug("Zone snapshots: {}", zoneSnapshot);
        Set<String> availableZones = ZoneAvoidanceRule.getAvailableZones(zoneSnapshot, triggeringLoad.get(), triggeringBlackoutPercentage.get());
        logger.debug("Available zones: {}", availableZones);
        if (availableZones != null) {
            return availableZones.contains(input.getServer().getZone());
        } else {
            return false;
        }
    }    
}
