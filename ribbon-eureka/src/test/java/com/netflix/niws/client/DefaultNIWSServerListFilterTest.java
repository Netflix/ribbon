package com.netflix.niws.client;

import static org.junit.Assert.*;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.config.ConfigurationManager;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.loadbalancer.LoadBalancerStats;

public class DefaultNIWSServerListFilterTest {
    @BeforeClass
    public static void init() {
        System.setProperty("EC2_AVAILABILITY_ZONE", "us-eAst-1C");    
        ConfigurationManager.getDeploymentContext().setDeploymentDatacenter("cloud");
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
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest1.niws.client.DeploymentContextBasedVipAddresses", "l10nservicegeneral.cloud.netflix.net:7001");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest1.niws.client.NFLoadBalancerClassName", "com.netflix.niws.client.NIWSDiscoveryLoadBalancer");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest1.niws.client.EnableZoneAffinity", "true");
        NIWSDiscoveryLoadBalancer lb = (NIWSDiscoveryLoadBalancer) ClientFactory.getNamedLoadBalancer("DefaultNIWSServerListFilterTest1");
        assertTrue(lb.getRule() instanceof AvailabilityFilteringRule);
        DefaultNIWSServerListFilter filter = (DefaultNIWSServerListFilter) lb.filter;
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
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest2.niws.client.DeploymentContextBasedVipAddresses", "l10nservicegeneral.cloud.netflix.net:7001");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest2.niws.client.NFLoadBalancerClassName", "com.netflix.niws.client.NIWSDiscoveryLoadBalancer");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest2.niws.client.EnableZoneExclusivity", "true");
        NIWSDiscoveryLoadBalancer lb = (NIWSDiscoveryLoadBalancer) ClientFactory.getNamedLoadBalancer("DefaultNIWSServerListFilterTest2");
        DefaultNIWSServerListFilter filter = (DefaultNIWSServerListFilter) lb.filter;
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
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest3.niws.client.DeploymentContextBasedVipAddresses", "l10nservicegeneral.cloud.netflix.net:7001");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest3.niws.client.NFLoadBalancerClassName", "com.netflix.niws.client.NIWSDiscoveryLoadBalancer");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest3.niws.client.EnableZoneAffinity", "true");
        ConfigurationManager.getConfigInstance().setProperty("DefaultNIWSServerListFilterTest3.niws.client.zoneAffinity.minAvailableServers", "3");
        NIWSDiscoveryLoadBalancer lb = (NIWSDiscoveryLoadBalancer) ClientFactory.getNamedLoadBalancer("DefaultNIWSServerListFilterTest3");
        DefaultNIWSServerListFilter filter = (DefaultNIWSServerListFilter) lb.filter;
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
