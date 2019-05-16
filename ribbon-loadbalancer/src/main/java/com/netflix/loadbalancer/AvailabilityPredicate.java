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

import com.google.common.base.Preconditions;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.config.Property;

import javax.annotation.Nullable;

/**
 * Predicate with the logic of filtering out circuit breaker tripped servers and servers 
 * with too many concurrent connections from this client.
 * 
 * @author awang
 *
 */
public class AvailabilityPredicate extends  AbstractServerPredicate {

    private static final IClientConfigKey<Boolean> FILTER_CIRCUIT_TRIPPED = new CommonClientConfigKey<Boolean>(
            "niws.loadbalancer.availabilityFilteringRule.filterCircuitTripped", true) {};

    private static final IClientConfigKey<Integer> DEFAULT_ACTIVE_CONNECTIONS_LIMIT = new CommonClientConfigKey<Integer>(
            "niws.loadbalancer.availabilityFilteringRule.activeConnectionsLimit", -1) {};

    private static final IClientConfigKey<Integer> ACTIVE_CONNECTIONS_LIMIT = new CommonClientConfigKey<Integer>(
            "ActiveConnectionsLimit", -1) {};

    private Property<Boolean> circuitBreakerFiltering = Property.of(FILTER_CIRCUIT_TRIPPED.defaultValue());
    private Property<Integer> defaultActiveConnectionsLimit = Property.of(DEFAULT_ACTIVE_CONNECTIONS_LIMIT.defaultValue());
    private Property<Integer> activeConnectionsLimit = Property.of(ACTIVE_CONNECTIONS_LIMIT.defaultValue());

    public AvailabilityPredicate(IRule rule, IClientConfig clientConfig) {
        super(rule);
        initDynamicProperty(clientConfig);
    }

    public AvailabilityPredicate(LoadBalancerStats lbStats, IClientConfig clientConfig) {
        super(lbStats);
        initDynamicProperty(clientConfig);
    }

    AvailabilityPredicate(IRule rule) {
        super(rule);
    }

    private void initDynamicProperty(IClientConfig clientConfig) {
        if (clientConfig != null) {
            this.circuitBreakerFiltering = clientConfig.getGlobalProperty(FILTER_CIRCUIT_TRIPPED);
            this.defaultActiveConnectionsLimit = clientConfig.getGlobalProperty(DEFAULT_ACTIVE_CONNECTIONS_LIMIT);
            this.activeConnectionsLimit = clientConfig.getDynamicProperty(ACTIVE_CONNECTIONS_LIMIT);
        }
    }

    private int getActiveConnectionsLimit() {
        Integer limit = activeConnectionsLimit.getOrDefault();
        if (limit == -1) {
            limit = defaultActiveConnectionsLimit.getOrDefault();
            if (limit == -1) {
                limit = Integer.MAX_VALUE;
            }
        }
        return limit;
    }

    @Override
    public boolean apply(@Nullable PredicateKey input) {
        LoadBalancerStats stats = getLBStats();
        if (stats == null) {
            return true;
        }
        return !shouldSkipServer(stats.getSingleServerStat(input.getServer()));
    }
    
    private boolean shouldSkipServer(ServerStats stats) {
        if ((circuitBreakerFiltering.getOrDefault() && stats.isCircuitBreakerTripped())
                || stats.getActiveRequestsCount() >= getActiveConnectionsLimit()) {
            return true;
        }
        return false;
    }

}
