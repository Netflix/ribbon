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

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.config.Property;
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

    private static final IClientConfigKey<Double> TRIGGERING_LOAD_PER_SERVER_THRESHOLD = new CommonClientConfigKey<Double>(
            "ZoneAwareNIWSDiscoveryLoadBalancer.%s.triggeringLoadPerServerThreshold", 0.2d) {};

    private static final IClientConfigKey<Double> AVOID_ZONE_WITH_BLACKOUT_PERCENTAGE = new CommonClientConfigKey<Double>(
            "ZoneAwareNIWSDiscoveryLoadBalancer.%s.avoidZoneWithBlackoutPercetage", 0.99999d) {};

    private static final IClientConfigKey<Boolean> ENABLED = new CommonClientConfigKey<Boolean>(
            "niws.loadbalancer.zoneAvoidanceRule.enabled", true) {};

    private Property<Double> triggeringLoad = Property.of(TRIGGERING_LOAD_PER_SERVER_THRESHOLD.defaultValue());

    private Property<Double> triggeringBlackoutPercentage = Property.of(AVOID_ZONE_WITH_BLACKOUT_PERCENTAGE.defaultValue());
    
    private Property<Boolean> enabled = Property.of(ENABLED.defaultValue());

    public ZoneAvoidancePredicate(IRule rule, IClientConfig clientConfig) {
        super(rule);
        initDynamicProperties(clientConfig);
    }

    public ZoneAvoidancePredicate(LoadBalancerStats lbStats, IClientConfig clientConfig) {
        super(lbStats);
        initDynamicProperties(clientConfig);
    }

    ZoneAvoidancePredicate(IRule rule) {
        super(rule);
    }

    private void initDynamicProperties(IClientConfig clientConfig) {
        if (clientConfig != null) {
            enabled = clientConfig.getGlobalProperty(ENABLED);
            triggeringLoad = clientConfig.getGlobalProperty(TRIGGERING_LOAD_PER_SERVER_THRESHOLD.format(clientConfig.getClientName()));
            triggeringBlackoutPercentage = clientConfig.getGlobalProperty(AVOID_ZONE_WITH_BLACKOUT_PERCENTAGE.format(clientConfig.getClientName()));
        }
    }

    @Override
    public boolean apply(@Nullable PredicateKey input) {
        if (!enabled.getOrDefault()) {
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
        Set<String> availableZones = ZoneAvoidanceRule.getAvailableZones(zoneSnapshot, triggeringLoad.getOrDefault(), triggeringBlackoutPercentage.getOrDefault());
        logger.debug("Available zones: {}", availableZones);
        if (availableZones != null) {
            return availableZones.contains(input.getServer().getZone());
        } else {
            return false;
        }
    }    
}
