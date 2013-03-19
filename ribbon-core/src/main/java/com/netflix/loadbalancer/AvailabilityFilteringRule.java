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

import java.util.List;

import com.netflix.client.config.IClientConfig;
import com.netflix.config.ChainedDynamicProperty;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;

/**
 * A load balancer rule that filters out servers that 
 * <li> are in circuit breaker tripped state due to consecutive connection or read failures, or
 * <li> have active connections that exceeds a configurable limit (default is Integer.MAX_VALUE). The property 
 * to change this limit is 
 * <pre>{@code
 * 
 * <clientName>.<nameSpace>.ActiveConnectionsLimit
 * 
 * }</pre>
 *
 * <p>
 *   
 * @author awang
 *
 */
public class AvailabilityFilteringRule extends ClientConfigEnabledRoundRobinRule {
    
    private ChainedDynamicProperty.IntProperty activeConnectionsLimit;
    
    private static final DynamicBooleanProperty CIRCUIT_BREAKER_FILTERING =
    		DynamicPropertyFactory.getInstance().getBooleanProperty("niws.loadbalancer.availabilityFilteringRule.filterCircuitTripped", true);

    private static final DynamicIntProperty ACTIVE_CONNECTIONS_LIMIT =
            DynamicPropertyFactory.getInstance().getIntProperty("niws.loadbalancer.availabilityFilteringRule.activeConnectionsLimit", Integer.MAX_VALUE);

    public AvailabilityFilteringRule() {
    	super();
    	activeConnectionsLimit = new ChainedDynamicProperty.IntProperty(ACTIVE_CONNECTIONS_LIMIT);     	
    }
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
    	super.initWithNiwsConfig(clientConfig);
    	String id = "default";
    	if (clientConfig != null) {
    	    id = clientConfig.getClientName();
        	activeConnectionsLimit = new ChainedDynamicProperty.IntProperty(id + "." + clientConfig.getNameSpace() + ".ActiveConnectionsLimit", ACTIVE_CONNECTIONS_LIMIT); 
    	}    	
    }

        
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
            if (shouldSkipServer(stats)) {
                // try again 
                server = super.choose(key);
                count--;                
            } else {
                break;
            }
        }                
        return server;
    }
    
    private boolean shouldSkipServer(ServerStats stats) {
        if ((CIRCUIT_BREAKER_FILTERING.get() && stats.isCircuitBreakerTripped()) 
                || stats.getActiveRequestsCount() >= activeConnectionsLimit.get()) {
        	return true;
        }
    	return false;
    }
    
    @Monitor(name="AvailableServersCount", type = DataSourceType.GAUGE)
    public int getAvailableServersCount() {
    	ILoadBalancer lb = getLoadBalancer();
    	List<Server> servers = lb.getServerList(false);
    	if (servers == null) {
    		return 0;
    	}
    	int count = servers.size();
        LoadBalancerStats lbStats = null;        
        if (lb instanceof AbstractLoadBalancer) {
        	lbStats = ((AbstractLoadBalancer) lb).getLoadBalancerStats();
        }
    	if (lbStats != null) {
    		for (Server server: servers) {
    			ServerStats stats = lbStats.getSingleServerStat(server);
    			if (shouldSkipServer(stats)) {
    				count--;
    			}
    		}
        }
        return count;    	
    }
}
