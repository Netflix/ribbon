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
package com.netflix.niws.loadbalancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.client.ClientFactory;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext.ContextKey;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.ZoneAffinityServerListFilter;

public class DefaultNIWSServerListFilterTest {
    @BeforeClass
    public static void init() {
    	ConfigurationManager.getDeploymentContext().setValue(ContextKey.zone, "us-eAst-1C");
    }
    
    private DiscoveryEnabledServer createServer(String host, String zone) {
        return createServer(host, 7001, zone);    
    }
    
    private DiscoveryEnabledServer createServer(String host, int port, String zone) {
        AmazonInfo amazonInfo = AmazonInfo.Builder.newBuilder().addMetadata(AmazonInfo.MetaDataKey.availabilityZone, zone).build();
        
        Builder builder = InstanceInfo.Builder.newBuilder();
        InstanceInfo info = builder.setAppName("l10nservicegeneral")
        .setDataCenterInfo(amazonInfo)
        .setHostName(host)
        .setPort(port)
        .build();
        DiscoveryEnabledServer server = new DiscoveryEnabledServer(info, false);
        server.setZone(zone);
        return server;
    }
    
    private DiscoveryEnabledServer createServer(int hostId, String zoneSuffix) {
        return createServer(zoneSuffix + "-" + "server" + hostId, "Us-east-1" + zoneSuffix);
    }

    @Test
    public void testZoneAffinityEnabled() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest1.ribbon.DeploymentContextBasedVipAddresses", "l10nservicegeneral.cloud.netflix.net:7001");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest1.ribbon.NFLoadBalancerClassName", DynamicServerListLoadBalancer.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest1.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());

        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest1.ribbon.EnableZoneAffinity", "true");
        DynamicServerListLoadBalancer lb = (DynamicServerListLoadBalancer) ClientFactory.getNamedLoadBalancer("DefaultNIWSServerListFilterTest1");
        assertTrue(lb.getRule() instanceof AvailabilityFilteringRule);
        ZoneAffinityServerListFilter filter = (ZoneAffinityServerListFilter) lb.getFilter();
        LoadBalancerStats loadBalancerStats = lb.getLoadBalancerStats();
        List<DiscoveryEnabledServer> servers = new ArrayList<DiscoveryEnabledServer>();        
        servers.add(createServer(1, "a"));
        servers.add(createServer(2, "a"));
        servers.add(createServer(3, "a"));
        servers.add(createServer(4, "a"));
        servers.add(createServer(1, "b"));
        servers.add(createServer(2, "b"));
        servers.add(createServer(3, "b"));
        servers.add(createServer(1, "c"));
        servers.add(createServer(2, "c"));
        servers.add(createServer(3, "c"));
        servers.add(createServer(4, "c"));
        servers.add(createServer(5, "c"));
        List<DiscoveryEnabledServer> filtered = filter.getFilteredListOfServers(servers);
        List<DiscoveryEnabledServer> expected = new ArrayList<DiscoveryEnabledServer>();
        expected.add(createServer(1, "c"));
        expected.add(createServer(2, "c"));
        expected.add(createServer(3, "c"));
        expected.add(createServer(4, "c"));
        expected.add(createServer(5, "c"));
        assertEquals(expected, filtered);
        lb.setServersList(filtered);        
        for (int i = 1; i <= 4; i++) {            
            loadBalancerStats.incrementActiveRequestsCount(createServer(i, "c"));
        }
        filtered = filter.getFilteredListOfServers(servers);
        assertEquals(servers, filtered);

    }

    
    @Test
    public void testZoneExclusivity() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest2.ribbon.DeploymentContextBasedVipAddresses", "l10nservicegeneral.cloud.netflix.net:7001");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest2.ribbon.NFLoadBalancerClassName", DynamicServerListLoadBalancer.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest2.ribbon.EnableZoneExclusivity", "true");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest2.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());
        DynamicServerListLoadBalancer lb = (DynamicServerListLoadBalancer) ClientFactory.getNamedLoadBalancer("DefaultNIWSServerListFilterTest2");
        ZoneAffinityServerListFilter filter = (ZoneAffinityServerListFilter) lb.getFilter();
        LoadBalancerStats loadBalancerStats = lb.getLoadBalancerStats();
        List<DiscoveryEnabledServer> servers = new ArrayList<DiscoveryEnabledServer>();        
        servers.add(createServer(1, "a"));
        servers.add(createServer(2, "a"));
        servers.add(createServer(3, "a"));
        servers.add(createServer(4, "a"));
        servers.add(createServer(1, "b"));
        servers.add(createServer(2, "b"));
        servers.add(createServer(3, "b"));
        servers.add(createServer(1, "c"));
        servers.add(createServer(2, "c"));
        servers.add(createServer(3, "c"));
        servers.add(createServer(4, "c"));
        servers.add(createServer(5, "c"));
        List<DiscoveryEnabledServer> filtered = filter.getFilteredListOfServers(servers);
        List<DiscoveryEnabledServer> expected = new ArrayList<DiscoveryEnabledServer>();
        expected.add(createServer(1, "c"));
        expected.add(createServer(2, "c"));
        expected.add(createServer(3, "c"));
        expected.add(createServer(4, "c"));
        expected.add(createServer(5, "c"));
        assertEquals(expected, filtered);
        lb.setServersList(filtered);        
        for (int i = 1; i <= 4; i++) {            
            loadBalancerStats.incrementActiveRequestsCount(createServer(i, "c"));
        }
        filtered = filter.getFilteredListOfServers(servers);
        assertEquals(expected, filtered);
    }
    
    @Test
    public void testZoneAffinityOverride() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest3.ribbon.DeploymentContextBasedVipAddresses", "l10nservicegeneral.cloud.netflix.net:7001");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest3.ribbon.NFLoadBalancerClassName", DynamicServerListLoadBalancer.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest3.ribbon.EnableZoneAffinity", "true");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest3.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest3.ribbon.zoneAffinity.minAvailableServers", "3");
        DynamicServerListLoadBalancer lb = (DynamicServerListLoadBalancer) ClientFactory.getNamedLoadBalancer("DefaultNIWSServerListFilterTest3");
        ZoneAffinityServerListFilter filter = (ZoneAffinityServerListFilter) lb.getFilter();
        LoadBalancerStats loadBalancerStats = lb.getLoadBalancerStats();
        List<DiscoveryEnabledServer> servers = new ArrayList<DiscoveryEnabledServer>();        
        servers.add(createServer(1, "a"));
        servers.add(createServer(2, "a"));
        servers.add(createServer(3, "a"));
        servers.add(createServer(4, "a"));
        servers.add(createServer(1, "b"));
        servers.add(createServer(2, "b"));
        servers.add(createServer(3, "b"));
        servers.add(createServer(1, "c"));
        servers.add(createServer(2, "c"));
        List<DiscoveryEnabledServer> filtered = filter.getFilteredListOfServers(servers);
        List<DiscoveryEnabledServer> expected = new ArrayList<DiscoveryEnabledServer>();
        /*
        expected.add(createServer(1, "c"));
        expected.add(createServer(2, "c"));
        expected.add(createServer(3, "c"));
        expected.add(createServer(4, "c"));
        expected.add(createServer(5, "c")); */
        // less than 3 servers in zone c, will not honor zone affinity
        assertEquals(servers, filtered);
        lb.setServersList(filtered);        
        servers.add(createServer(3, "c"));
        filtered = filter.getFilteredListOfServers(servers);
        expected.add(createServer(1, "c"));
        expected.add(createServer(2, "c"));
        expected.add(createServer(3, "c"));
        filtered = filter.getFilteredListOfServers(servers);
        // now we have enough servers in C
        assertEquals(expected, filtered);

        // make one server black out
        for (int i = 1; i <= 3; i++) {            
            loadBalancerStats.incrementSuccessiveConnectionFailureCount(createServer(1, "c"));
        }
        filtered = filter.getFilteredListOfServers(servers);
        assertEquals(servers, filtered);
        // new server added in zone c, zone c should now have enough servers
        servers.add(createServer(4, "c"));
        filtered = filter.getFilteredListOfServers(servers);
        expected.add(createServer(4, "c"));
        assertEquals(expected, filtered);
    } 
}
