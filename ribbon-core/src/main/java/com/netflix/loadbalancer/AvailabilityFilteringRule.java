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

import com.google.common.collect.Collections2;
import com.netflix.client.config.IClientConfig;
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
public class AvailabilityFilteringRule extends PredicateBasedRule {    

    private AbstractServerPredicate predicate;
    
    public AvailabilityFilteringRule() {
    	super();
    	predicate = CompositePredicate.withPredicate(new AvailabilityPredicate(this, null))
                .addFallbackPredicate(AbstractServerPredicate.alwaysTrue())
                .build();
    }
    
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
    	predicate = CompositePredicate.withPredicate(new AvailabilityPredicate(this, clientConfig))
    	            .addFallbackPredicate(AbstractServerPredicate.alwaysTrue())
    	            .build();
    }

    @Monitor(name="AvailableServersCount", type = DataSourceType.GAUGE)
    public int getAvailableServersCount() {
    	ILoadBalancer lb = getLoadBalancer();
    	List<Server> servers = lb.getServerList(false);
    	if (servers == null) {
    		return 0;
    	}
    	return Collections2.filter(servers, predicate.getServerOnlyPredicate()).size();
    }


    /**
     * This method is overridden to provide a more efficient implementation which does not iterate through
     * all servers. This is under the assumption that in most cases, there are more available instances 
     * than not. 
     */
    @Override
    public Server choose(Object key) {
        int count = 0;
        Server server = roundRobinRule.choose(key);
        while (count++ <= 10) {
            if (predicate.apply(new PredicateKey(server))) {
                return server;
            }
            server = roundRobinRule.choose(key);
        }
        return super.choose(key);
    }

    @Override
    public AbstractServerPredicate getPredicate() {
        return predicate;
    }
}
