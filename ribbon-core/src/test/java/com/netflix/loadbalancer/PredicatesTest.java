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

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext.ContextKey;

public class PredicatesTest {
    
    static {
        ConfigurationManager.getConfigInstance().setProperty("niws.loadbalancer.default.circuitTripTimeoutFactorSeconds", 100000);
        ConfigurationManager.getConfigInstance().setProperty("niws.loadbalancer.default.circuitTripMaxTimeoutSeconds", 1000000);
    }
    
    @AfterClass
    public static void cleanup() {
        ConfigurationManager.getConfigInstance().clearProperty("niws.loadbalancer.default.circuitTripTimeoutFactorSeconds");
        ConfigurationManager.getConfigInstance().clearProperty("niws.loadbalancer.default.circuitTripMaxTimeoutSeconds");        
    }
    
    public void setServerStats(LoadBalancerStats lbStats, Object[][] stats) {
        for (Object[] serverStats: stats) {
            Server server = (Server) serverStats[0];
            Boolean circuitTripped = (Boolean) serverStats[1];
            Integer activeConnections = (Integer) serverStats[2];
            ServerStats ss = lbStats.getSingleServerStat(server);
            if (circuitTripped) {
                for (int i = 0; i < 3; i++) {
                    ss.incrementSuccessiveConnectionFailureCount();
                }
            }
            for (int i = 0; i < activeConnections; i++) {
                ss.incrementActiveRequestsCount();
            }
        }
    }
    
    @Test
    public void testAvalabilityPredicate() {
        Object[][] stats = new Object[10][3];
        List<Server> expectedFiltered = Lists.newArrayList();
        for (int i = 0; i < 7; i++) {
            stats[i] = new Object[3];
            stats[i][0] = new Server("good:" + i);
            stats[i][1] = false;
            stats[i][2] = 0;
            expectedFiltered.add((Server) stats[i][0]);
        }        
        for (int i = 7; i < 10; i++) {
            stats[i] = new Object[3];
            stats[i][0] = new Server("bad:" + i);
            stats[i][1] = true;
            stats[i][2] = 0;
        }
        LoadBalancerStats lbStats = new LoadBalancerStats("default");
        setServerStats(lbStats, stats);
        AvailabilityPredicate predicate = new AvailabilityPredicate(lbStats, null);
        assertFalse(predicate.apply(new PredicateKey((Server) stats[8][0])));
        assertTrue(predicate.apply(new PredicateKey((Server) stats[0][0])));
        List<Server> servers = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            servers.add((Server) stats[i][0]);
        }
        List<Server> filtered = predicate.getEligibleServers(servers);
        assertEquals(expectedFiltered, filtered);
        Set<Server> chosen = Sets.newHashSet();
        for (int i = 0; i < 20; i++) {
            Server s = predicate.chooseRoundRobinAfterFiltering(servers).get();
            assertEquals("good:" + (i % 7), s.getId());
            chosen.add(s);
        }
        assertEquals(7, chosen.size());
    }
    
    @Test
    public void testZoneAvoidancePredicate() {
        Object[][] stats = new Object[10][3];
        Map<String, List<Server>> zoneMap = Maps.newHashMap();
        List<Server> expectedFiltered = Lists.newArrayList();
        List<Server> list0 = Lists.newArrayList();
        for (int i = 0; i < 3; i++) {
            stats[i] = new Object[3];
            stats[i][0] = new Server("good:" + i);
            ((Server) stats[i][0]).setZone("0");
            list0.add((Server) stats[i][0]);
            stats[i][1] = false;
            stats[i][2] = 0;
            expectedFiltered.add((Server) stats[i][0]);
        }        
        zoneMap.put("0", list0);
        List<Server> list1 = Lists.newArrayList();
        for (int i = 3; i < 7; i++) {
            stats[i] = new Object[3];
            stats[i][0] = new Server("bad:" + i);
            ((Server) stats[i][0]).setZone("1");
            list1.add((Server) stats[i][0]);
            stats[i][1] = false;
            stats[i][2] = 2;
        }
        zoneMap.put("1", list1);
        List<Server> list2 = Lists.newArrayList();
        for (int i = 7; i < 10; i++) {
            stats[i] = new Object[3];
            stats[i][0] = new Server("good:" + i);
            ((Server) stats[i][0]).setZone("2");
            list2.add((Server) stats[i][0]);
            stats[i][1] = false;
            stats[i][2] = 1;
        }
        zoneMap.put("2", list2);
        LoadBalancerStats lbStats = new LoadBalancerStats("default");
        setServerStats(lbStats, stats);
        lbStats.updateZoneServerMapping(zoneMap);
        ZoneAvoidancePredicate predicate = new ZoneAvoidancePredicate(lbStats, null);
        assertFalse(predicate.apply(new PredicateKey((Server) stats[5][0])));
        assertTrue(predicate.apply(new PredicateKey((Server) stats[0][0])));
        assertTrue(predicate.apply(new PredicateKey((Server) stats[9][0])));
    }
    
    @Test
    public void testCompositePredicate() {
        Object[][] stats = new Object[10][3];
        Map<String, List<Server>> zoneMap = Maps.newHashMap();
        List<Server> expectedFiltered = Lists.newArrayList();
        for (int i = 0; i < 3; i++) {
            stats[i] = new Object[3];
            stats[i][0] = new Server("good:" + i);
            ((Server) stats[i][0]).setZone("0");
            stats[i][1] = false;
            stats[i][2] = 0;
            expectedFiltered.add((Server) stats[i][0]);
        }        
        List<Server> list1 = Lists.newArrayList();
        for (int i = 3; i < 7; i++) {
            stats[i] = new Object[3];
            stats[i][0] = new Server("bad:" + i);
            ((Server) stats[i][0]).setZone("0");
            list1.add((Server) stats[i][0]);
            stats[i][1] = true;
            stats[i][2] = 0;
        }
        zoneMap.put("1", list1);
        List<Server> list2 = Lists.newArrayList();
        for (int i = 7; i < 10; i++) {
            stats[i] = new Object[3];
            stats[i][0] = new Server("good:" + i);
            ((Server) stats[i][0]).setZone("1");
            list2.add((Server) stats[i][0]);
            stats[i][1] = false;
            stats[i][2] = 0;
        }
        zoneMap.put("2", list2);
        LoadBalancerStats lbStats = new LoadBalancerStats("default");
        setServerStats(lbStats, stats);
        lbStats.updateZoneServerMapping(zoneMap);
        ConfigurationManager.getDeploymentContext().setValue(ContextKey.zone, "0");
        AvailabilityPredicate p1 = new AvailabilityPredicate(lbStats, null);
        ZoneAffinityPredicate p2 = new ZoneAffinityPredicate();
        CompositePredicate c = CompositePredicate.withPredicates(p2, p1).build();
        assertFalse(c.apply(new PredicateKey((Server) stats[5][0])));
        assertTrue(c.apply(new PredicateKey((Server) stats[0][0])));
        assertFalse(c.apply(new PredicateKey((Server) stats[9][0])));
        List<Server> servers = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            servers.add((Server) stats[i][0]);
        }
        List<Server> filtered = c.getEligibleServers(servers);
        assertEquals(3, filtered.size());
        CompositePredicate c2 = CompositePredicate.withPredicates(p2, p1)
                .setFallbackThresholdAsMinimalFilteredNumberOfServers(5)
                .addFallbackPredicate(p1).build();
        filtered = c2.getEligibleServers(servers);
        assertEquals(6, filtered.size());
    }

}
