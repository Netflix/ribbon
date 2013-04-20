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


    @Override
    public AbstractServerPredicate getPredicate() {
        return predicate;
    }
}
