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
package com.netflix.niws.client;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;

public class AvailabilityFilteringRule extends NIWSRoundRobinRule {
    
    private static final DynamicBooleanProperty CIRCUIT_BREAKER_FILTERING =
        DynamicPropertyFactory.getInstance().getBooleanProperty("niws.loadbalancer.availabilityFilteringRule.filterCircuitTripped", true);

    private static final DynamicIntProperty ACTIVE_CONNECTIONS_LIMIT =
        DynamicPropertyFactory.getInstance().getIntProperty("niws.loadbalancer.availabilityFilteringRule.activeConnectionsLimit", Integer.MAX_VALUE);

        
    @Override
    public Server choose(BaseLoadBalancer lb, Object key) {
        LoadBalancerStats lbStats = lb.getLoadBalancerStats();
        Server server = super.choose(lb, key);
        if (lbStats == null) {
            return server;
        }
        int count = lb.getServerList(false).size();
        while (server != null && count > 0) {
            ServerStats stats = lbStats.getSingleServerStat(server);
            if ((CIRCUIT_BREAKER_FILTERING.get() && stats.isCircuitBreakerTripped()) 
                    || stats.getActiveRequestsCount() >= ACTIVE_CONNECTIONS_LIMIT.get()) {
                // try again 
                server = super.choose(lb, key);
                count--;                
            } else {
                break;
            }
        }                
        return server;
    }
}
