/*
 *
 * Copyright 2014 Netflix, Inc.
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


import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class BestAvailableRuleTest {
    
    @Test
    public void testRule() {
        List<Server> servers = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            servers.add(new Server(String.valueOf(i), 80));
        }
        IRule rule = new BestAvailableRule();
        BaseLoadBalancer lb = LoadBalancerBuilder.newBuilder().withRule(rule).buildFixedServerListLoadBalancer(servers);
        for (int i = 0; i < 10; i++) {
            ServerStats stats = lb.getLoadBalancerStats().getSingleServerStat(servers.get(i));
            for (int j = 0; j < 10 - i; j++) {
                stats.incrementActiveRequestsCount();
            }
        }
        Server server = lb.chooseServer();
        assertEquals(servers.get(9), server);
        ServerStats stats = lb.getLoadBalancerStats().getSingleServerStat(servers.get(9));
        for (int i = 0; i < 3; i++) {
            stats.incrementSuccessiveConnectionFailureCount();            
        }
        server = lb.chooseServer();
        // server 9 has tripped circuit breaker
        assertEquals(servers.get(8), server);

    }
}
