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

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;

public class AvailabilityFilteringRule extends ClientConfigEnabledRoundRobinRule {
    
    private static final DynamicBooleanProperty CIRCUIT_BREAKER_FILTERING =
        DynamicPropertyFactory.getInstance().getBooleanProperty("niws.loadbalancer.availabilityFilteringRule.filterCircuitTripped", true);

    private static final DynamicIntProperty ACTIVE_CONNECTIONS_LIMIT =
        DynamicPropertyFactory.getInstance().getIntProperty("niws.loadbalancer.availabilityFilteringRule.activeConnectionsLimit", Integer.MAX_VALUE);

        
    @Override
    public Server choose(Object key) {
    	ILoadBalancer lb = getLoadBalancer();
        Server server = super.choose(key);
        LoadBalancerStats lbStats = null;
        
        if (lb instanceof AbstractLoadBalancer) {
        	lbStats = ((AbstractLoadBalancer) lb).getLoadBalancerStats();
        }
        if (lbStats == null) {
            return server;
        }
        int count = lb.getServerList(false).size();
        while (server != null && count > 0) {
            ServerStats stats = lbStats.getSingleServerStat(server);
            if ((CIRCUIT_BREAKER_FILTERING.get() && stats.isCircuitBreakerTripped()) 
                    || stats.getActiveRequestsCount() >= ACTIVE_CONNECTIONS_LIMIT.get()) {
                // try again 
                server = super.choose(key);
                count--;                
            } else {
                break;
            }
        }                
        return server;
    }
}
