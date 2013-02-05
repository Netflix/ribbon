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
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.config.IClientConfig;

/**
 * The most well known and basic loadbalacing strategy, i.e. Round Robin Rule.
 * 
 * @author stonse
 * 
 */
public class RoundRobinRule extends AbstractLoadBalancerRule {
    AtomicInteger nextIndexAI;

    private static Logger log = LoggerFactory.getLogger(RoundRobinRule.class);

    public RoundRobinRule() {
        nextIndexAI = new AtomicInteger(0);
    }

    public RoundRobinRule(ILoadBalancer lb) {
    	this();
    	setLoadBalancer(lb);
    }

    /*
     * Rotate over all known servers.
     */
    final static boolean availableOnly = false;

    public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            log.warn("no load balancer");
            return null;
        }
        Server server = null;
        int index = 0;

        int count = 0;
        while (server == null && count++ < 10) {
            List<Server> upList = lb.getServerList(true);
            List<Server> allList = lb.getServerList(false);
            int upCount = upList.size();
            int serverCount = allList.size();

            if ((upCount == 0) || (serverCount == 0)) {
                log.warn("No up servers available from load balancer: " + lb);
                return null;
            }

            index = nextIndexAI.incrementAndGet() % serverCount;
            server = allList.get(index);

            if (server == null) {
                /* Transient. */
                Thread.yield();
                continue;
            }

            if (server.isAlive() && (server.isReadyToServe())) {
                return (server);
            }

            // Next.
            server = null;
        }

        if (count >= 10) {
            log.warn("No available alive servers after 10 tries from load balancer: "
                    + lb);
        }
        return server;
    }

	@Override
	public Server choose(Object key) {
		return choose(getLoadBalancer(), key);
	}

	@Override
	public void initWithNiwsConfig(IClientConfig clientConfig) {
	}
}
