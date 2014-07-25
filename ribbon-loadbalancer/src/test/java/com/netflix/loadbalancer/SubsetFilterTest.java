/**
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.loadbalancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.ConfigurationManager;

public class SubsetFilterTest {
    
    @BeforeClass
    public static void init() {
        Configuration config = ConfigurationManager.getConfigInstance();
        config.setProperty(
                DefaultClientConfigImpl.DEFAULT_PROPERTY_NAME_SPACE + ".ServerListSubsetFilter.forceEliminatePercent", "0.6");
        config.setProperty(
                DefaultClientConfigImpl.DEFAULT_PROPERTY_NAME_SPACE + ".ServerListSubsetFilter.eliminationFailureThresold", 2);
        config.setProperty(
                DefaultClientConfigImpl.DEFAULT_PROPERTY_NAME_SPACE + ".ServerListSubsetFilter.eliminationConnectionThresold", 2);
        config.setProperty(
                DefaultClientConfigImpl.DEFAULT_PROPERTY_NAME_SPACE + ".ServerListSubsetFilter.size", "5");
        
        config.setProperty("SubsetFilerTest.ribbon.NFLoadBalancerClassName", 
                com.netflix.loadbalancer.DynamicServerListLoadBalancer.class.getName());
        config.setProperty("SubsetFilerTest.ribbon.NIWSServerListClassName", MockServerList.class.getName());
        config.setProperty("SubsetFilerTest.ribbon.NIWSServerListFilterClassName", ServerListSubsetFilter.class.getName());
        // turn off auto refresh
        config.setProperty("SubsetFilerTest.ribbon.ServerListRefreshInterval", String.valueOf(Integer.MAX_VALUE));
        config.setProperty("SubsetFilerTest.ribbon.ServerListSubsetFilter.forceEliminatePercent", "0.6");
        config.setProperty("SubsetFilerTest.ribbon.ServerListSubsetFilter.eliminationFailureThresold", 2);
        config.setProperty("SubsetFilerTest.ribbon.ServerListSubsetFilter.eliminationConnectionThresold", 2);
        config.setProperty("SubsetFilerTest.ribbon.ServerListSubsetFilter.size", "5");
    }

    List<Server> getServersAndStats(LoadBalancerStats lbStats, Object[][] stats) {
        List<Server> list = Lists.newArrayList();
        for (Object[] serverStats: stats) {
            Server server = new Server((String) serverStats[0]);
            list.add(server);
            int failureCount = (Integer) serverStats[1];
            int connectionCount = (Integer) serverStats[2];
            lbStats.getServerStats().put(server, new DummyServerStats(connectionCount, failureCount));
        }
        return list;
    }
    
    @Test
    public void testSorting() {
        ServerListSubsetFilter<Server> filter = new ServerListSubsetFilter<Server>();
        LoadBalancerStats stats = new LoadBalancerStats("default");
        filter.setLoadBalancerStats(stats);
        Object[][] serverStats = { 
                {"server0", 0, 0},
                {"server1", 1, 0},
                {"server2", 1, 1},
                {"server3", 0, 1},
                {"server4", 2, 0}
        };
        List<Server> servers = getServersAndStats(stats, serverStats);
        Collections.sort(servers, filter);
        List<String> expected = Lists.newArrayList("server4", "server2", "server1", "server3", "server0");
        for (int i = 0; i < servers.size(); i++) {
            assertEquals(expected.get(i), servers.get(i).getHost());
        }
    }
    
    @Test
    public void testFiltering() {
        ServerListSubsetFilter<Server> filter = new ServerListSubsetFilter<Server>();
        LoadBalancerStats stats = new LoadBalancerStats("default");
        filter.setLoadBalancerStats(stats);
        Object[][] serverStats = { 
                {"server0", 0, 0},
                {"server1", 0, 0},
                {"server2", 0, 0},
                {"server3", 0, 0},
                {"server4", 0, 0},
                {"server5", 0, 0},
                {"server6", 0, 0},
                {"server7", 0, 0},
                {"server8", 0, 0},
                {"server9", 0, 0}
        };
        List<Server> list = getServersAndStats(stats, serverStats);
        List<Server> filtered = filter.getFilteredListOfServers(list);
        // first filtering, should get 5 servers 
        assertEquals(filtered.size(), 5);
        
        Server s1 = filtered.get(0);        
        Server s2 = filtered.get(1);
        Server s3 = filtered.get(2);
        Server s4 = filtered.get(3);
        Server s5 = filtered.get(4);

        // failure count > threshold
        DummyServerStats stats1 = (DummyServerStats) stats.getSingleServerStat(s1);
        stats1.setConnectionFailureCount(3);
        
        // active requests count > threshold
        DummyServerStats stats2 = (DummyServerStats) stats.getSingleServerStat(s2);
        stats2.setActiveRequestsCount(3);

        // will be forced eliminated after sorting 
        DummyServerStats stats3 = (DummyServerStats) stats.getSingleServerStat(s3);
        stats3.setActiveRequestsCount(2);

        // will be forced eliminated after sorting
        DummyServerStats stats4 = (DummyServerStats) stats.getSingleServerStat(s4);
        stats4.setConnectionFailureCount(1);

        // filter again, this time some servers will be eliminated
        filtered = filter.getFilteredListOfServers(list);
        
        assertEquals(5, filtered.size());
        assertTrue(!filtered.contains(s1));
        assertTrue(!filtered.contains(s2));
        assertTrue(filtered.contains(s3));
        assertTrue(!filtered.contains(s4));
        assertTrue(filtered.contains(s5));
        
        // Not enough healthy servers, just get whatever is available
        List<Server> lastFiltered = filter.getFilteredListOfServers(Lists.newArrayList(filtered));
        assertEquals(5, lastFiltered.size());
    }
    
    @Test
    public void testWithLoadBalancer() {
        DynamicServerListLoadBalancer<Server> lb = (DynamicServerListLoadBalancer<Server>) 
                ClientFactory.getNamedLoadBalancer("SubsetFilerTest");        
        MockServerList serverList = (MockServerList) lb.getServerListImpl();
        Object[][] serverStats = { 
                {"server0", 0, 0},
                {"server1", 0, 0},
                {"server2", 0, 0},
                {"server3", 0, 0},
                {"server4", 0, 0},
                {"server5", 0, 0},
                {"server6", 0, 0},
                {"server7", 0, 0},
                {"server8", 0, 0},
                {"server9", 0, 0}
        };
        LoadBalancerStats stats = lb.getLoadBalancerStats();
        List<Server> list = getServersAndStats(stats, serverStats);
        serverList.setServerList(list);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        lb.updateListOfServers();
        List<Server> filtered = lb.getServerList(false);
        // first filtering, should get 5 servers 
        assertEquals(filtered.size(), 5);
        
        Server s1 = filtered.get(0);        
        Server s2 = filtered.get(1);
        Server s3 = filtered.get(2);
        Server s4 = filtered.get(3);
        Server s5 = filtered.get(4);

        // failure count > threshold
        DummyServerStats stats1 = (DummyServerStats) stats.getSingleServerStat(s1);
        stats1.setConnectionFailureCount(3);
        
        // active requests count > threshold
        DummyServerStats stats2 = (DummyServerStats) stats.getSingleServerStat(s2);
        stats2.setActiveRequestsCount(3);

        // will be forced eliminated after sorting 
        DummyServerStats stats3 = (DummyServerStats) stats.getSingleServerStat(s3);
        stats3.setActiveRequestsCount(2);

        // will be forced eliminated after sorting
        DummyServerStats stats4 = (DummyServerStats) stats.getSingleServerStat(s4);
        stats4.setConnectionFailureCount(1);

        // filter again, this time some servers will be eliminated
        serverList.setServerList(list);
        lb.updateListOfServers();
        filtered = lb.getServerList(false);
        
        assertEquals(5, filtered.size());
        assertTrue(!filtered.contains(s1));
        assertTrue(!filtered.contains(s2));
        assertTrue(filtered.contains(s3));
        assertTrue(!filtered.contains(s4));
        assertTrue(filtered.contains(s5));
        
        // Not enough healthy servers, just get whatever is available
        serverList.setServerList(Lists.newArrayList(filtered));
        lb.updateListOfServers();
        List<Server> lastFiltered = lb.getServerList(false);
        assertEquals(5, lastFiltered.size());

    }
    
}


class DummyServerStats extends ServerStats {

    int activeRequestsCount;
    
    int connectionFailureCount;
    
    public DummyServerStats(int activeRequestsCount, int connectionFailureCount) {
        this.activeRequestsCount = activeRequestsCount;
        this.connectionFailureCount = connectionFailureCount;                
    }
    
    public final void setActiveRequestsCount(int activeRequestsCount) {
        this.activeRequestsCount = activeRequestsCount;
    }

    public final void setConnectionFailureCount(int connectionFailureCount) {
        this.connectionFailureCount = connectionFailureCount;
    }

    @Override
    public int getActiveRequestsCount() {
        return activeRequestsCount;
    }

    @Override
    public long getFailureCount() {
        return connectionFailureCount;
    }
    
}
