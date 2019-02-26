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

import javax.annotation.Nullable;

/**
 * Predicate with the logic of filtering out circuit breaker tripped servers and servers 
 * with too many concurrent connections from this client.
 * 
 * @author awang
 *
 */
public class AvailabilityPredicate extends  AbstractServerPredicate {
        
    private DynamicProperty<Boolean> circuitBreakerFiltering;
    private DynamicProperty<Integer> defaultActiveConnectionsLimit;
    private DynamicProperty<Integer> activeConnectionsLimit;

    private final DynamicPropertyRepository repository;

    public AvailabilityPredicate(IRule rule, IClientConfig clientConfig) {
        super(rule);
        this.repository = clientConfig == null ? DynamicPropertyRepository.DEFAULT : clientConfig.getDynamicPropertyRepository();
        initDynamicProperty(clientConfig);
    }

    public AvailabilityPredicate(LoadBalancerStats lbStats, IClientConfig clientConfig) {
        super(lbStats);
        this.repository = clientConfig == null ? DynamicPropertyRepository.DEFAULT :  clientConfig.getDynamicPropertyRepository();
        initDynamicProperty(clientConfig);
    }

    AvailabilityPredicate(IRule rule) {
        super(rule);
        this.repository = DynamicPropertyRepository.DEFAULT;
        initDynamicProperty(null);
    }

    private void initDynamicProperty(IClientConfig clientConfig) {
        this.circuitBreakerFiltering = repository.getProperty("niws.loadbalancer.availabilityFilteringRule.filterCircuitTripped", Boolean.class, true);
        this.defaultActiveConnectionsLimit = repository.getProperty("niws.loadbalancer.availabilityFilteringRule.activeConnectionsLimit", Integer.class, Integer.MAX_VALUE);

        if (clientConfig != null) {
            activeConnectionsLimit = repository.getProperty(
                    clientConfig.getClientName() + "." + clientConfig.getNameSpace() + ".ActiveConnectionsLimit",
                    Integer.class,
                    Integer.MAX_VALUE);
        } else {
            activeConnectionsLimit = defaultActiveConnectionsLimit;
        }
    }

    private int getActiveConnectionsLimit() {
        Integer limit = activeConnectionsLimit.get();
        if (limit == null) {
            limit = defaultActiveConnectionsLimit.get();
            if (limit == null) {
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
        if ((circuitBreakerFiltering.get() && stats.isCircuitBreakerTripped())
                || stats.getActiveRequestsCount() >= getActiveConnectionsLimit()) {
            return true;
        }
        return false;
    }

}
