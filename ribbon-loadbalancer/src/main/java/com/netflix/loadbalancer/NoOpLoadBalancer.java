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

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A noOp Loadbalancer
 * i.e. doesnt do anything "loadbalancer like"
 * 
 * @author stonse
 *
 */
public class NoOpLoadBalancer extends AbstractLoadBalancer {

    static final Logger  logger = LoggerFactory.getLogger(NoOpLoadBalancer.class);
    
    
    @Override
    public void addServers(List<Server> newServers) {
        logger.info("addServers to NoOpLoadBalancer ignored");
    }

    @Override
    public Server chooseServer(Object key) {       
        return null;
    }

    @Override
    public LoadBalancerStats getLoadBalancerStats() {        
        return null;
    }

    
    @Override
    public List<Server> getServerList(ServerGroup serverGroup) {     
        return Collections.emptyList();
    }

    @Override
    public void markServerDown(Server server) {
        logger.info("markServerDown to NoOpLoadBalancer ignored");
    }

	@Override
	public List<Server> getServerList(boolean availableOnly) {
		// TODO Auto-generated method stub
		return null;
	}    
}
