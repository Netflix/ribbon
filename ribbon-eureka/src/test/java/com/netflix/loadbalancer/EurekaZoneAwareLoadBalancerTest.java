package com.netflix.loadbalancer;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.ClientFactory;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * (Mostly) ported over from {@link com.netflix.loadbalancer.ZoneAwareLoadBalancerTest}
 *
 * @author David Liu
 */
public class EurekaZoneAwareLoadBalancerTest {

    private final String vipAddress = "foobar";
    private final Map<String, DiscoveryEnabledServer> hostMap = new HashMap<String, DiscoveryEnabledServer>();

    private DiscoveryEnabledServer createServer(InstanceInfo instanceInfo) {
        DiscoveryEnabledServer server = new DiscoveryEnabledServer(instanceInfo, false);
        AmazonInfo dcInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();
        server.setZone(dcInfo.get(AmazonInfo.MetaDataKey.availabilityZone));
        server.setAlive(true);
        return server;
    }

    private DiscoveryEnabledServer createServer(int hostId, String zoneSuffix) {
        String uniqKey = hostMapKey(hostId, zoneSuffix);
        InstanceInfo instanceInfo = new InstanceInfo.Builder(InstanceInfoGenerator.takeOne())
                .setInstanceId(uniqKey)
                .setHostName(uniqKey)
                .setVIPAddress(vipAddress)
                .build();

        ((AmazonInfo) instanceInfo.getDataCenterInfo()).getMetadata()
                .put(AmazonInfo.MetaDataKey.availabilityZone.getName(), "us-east-1" + zoneSuffix);
        DiscoveryEnabledServer server = createServer(instanceInfo);

        hostMap.put(uniqKey, server);
        return server;
    }

    private DiscoveryEnabledServer getServer(int hostId, String zoneSuffix) {
        return hostMap.get(hostMapKey(hostId, zoneSuffix));
    }

    private String hostMapKey(int hostId, String zoneSuffix) {
        return hostId + zoneSuffix;
    }


    private void testChooseServer(EurekaZoneAwareLoadBalancer balancer, String... expectedZones) {
        Set<String> result = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            Server server = balancer.chooseServer(null);
            String zone = server.getZone().toLowerCase();
            result.add(zone);
        }
        Set<String> expected = new HashSet<String>();
        expected.addAll(Arrays.asList(expectedZones));
        assertEquals(expected, result);
    }


    @Test
    public void testChooseZone() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("niws.loadbalancer.serverStats.activeRequestsCount.effectiveWindowSeconds", 10);
        EurekaZoneAwareLoadBalancer balancer = new EurekaZoneAwareLoadBalancer();
        balancer.init();
        IRule globalRule = new RoundRobinRule();
        balancer.setRule(globalRule);
        LoadBalancerStats loadBalancerStats = balancer.getLoadBalancerStats();
        assertNotNull(loadBalancerStats);
        List<Server> servers = new ArrayList<Server>();

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
        balancer.setServersList(servers);
        balancer.setUpServerList(servers);
        assertTrue(balancer.getLoadBalancer("us-east-1a").getRule() instanceof RoundRobinRule);
        assertNotSame(globalRule, balancer.getLoadBalancer("us-east-1a").getRule());
        // System.out.println("=== LB Stats at testChooseZone 1: " + loadBalancerStats);
        testChooseServer(balancer, "us-east-1a", "us-east-1b", "us-east-1c");

        loadBalancerStats.incrementActiveRequestsCount(getServer(1, "c"));
        loadBalancerStats.incrementActiveRequestsCount(getServer(3, "c"));
        loadBalancerStats.incrementActiveRequestsCount(getServer(3, "c"));
        loadBalancerStats.decrementActiveRequestsCount(getServer(3, "c"));

        assertEquals(2, loadBalancerStats.getActiveRequestsCount("us-east-1c"));
        assertEquals(0.5d, loadBalancerStats.getActiveRequestsPerServer("us-east-1c"), 0.0001d);
        testChooseServer(balancer, "us-east-1a", "us-east-1b");
        for (int i = 0; i < 3; i++) {
            loadBalancerStats.incrementSuccessiveConnectionFailureCount(getServer(1, "a"));
        }


        assertEquals(1, loadBalancerStats.getCircuitBreakerTrippedCount("us-east-1a"));
        loadBalancerStats.incrementSuccessiveConnectionFailureCount(getServer(2, "b"));
        assertEquals(0, loadBalancerStats.getCircuitBreakerTrippedCount("us-east-1b"));
        // loadPerServer on both zone a and b should still be 0

        testChooseServer(balancer, "us-east-1a", "us-east-1b");

        // make a load on zone a
        loadBalancerStats.incrementActiveRequestsCount(getServer(2, "a"));
        assertEquals(1d/3, loadBalancerStats.getActiveRequestsPerServer("us-east-1a"), 0.0001);

        // zone c will be dropped as the worst zone
        testChooseServer(balancer, "us-east-1b", "us-east-1a");

        Thread.sleep(15000);
        assertEquals(0, loadBalancerStats.getCircuitBreakerTrippedCount("us-east-1a"));
        assertEquals(3, loadBalancerStats.getSingleServerStat(getServer(1, "a")).getSuccessiveConnectionFailureCount());
        assertEquals(0, loadBalancerStats.getActiveRequestsCount("us-east-1c"));
        assertEquals(0, loadBalancerStats.getSingleServerStat(getServer(1, "c")).getActiveRequestsCount());
        loadBalancerStats.clearSuccessiveConnectionFailureCount(createServer(1, "a"));
        assertEquals(0, loadBalancerStats.getSingleServerStat(getServer(1, "a")).getSuccessiveConnectionFailureCount());
        assertEquals(0, loadBalancerStats.getCircuitBreakerTrippedCount("us-east-1a"));

        loadBalancerStats.decrementActiveRequestsCount(getServer(2, "a"));

        assertEquals(0, loadBalancerStats.getActiveRequestsPerServer("us-east-1b"), 0.000001);
        assertEquals(0, loadBalancerStats.getActiveRequestsPerServer("us-east-1c"), 0.000001);

        assertEquals(0, loadBalancerStats.getActiveRequestsPerServer("us-east-1a"), 0.000001);

        testChooseServer(balancer, "us-east-1a", "us-east-1b", "us-east-1c");

        List<Server> emptyList = Collections.emptyList();
        Map<String, List<Server>> map = new HashMap<String, List<Server>>();
        map.put("us-east-1a", emptyList);
        map.put("us-east-1b", emptyList);
        balancer.setServerListForZones(map);
        testChooseServer(balancer, "us-east-1a", "us-east-1b", "us-east-1c");
    }

    @Test
    public void testZoneOutage() throws Exception {
        ConfigurationManager.getConfigInstance().clearProperty("niws.loadbalancer.serverStats.activeRequestsCount.effectiveWindowSeconds");
        EurekaZoneAwareLoadBalancer balancer = new EurekaZoneAwareLoadBalancer();
        balancer.init();
        LoadBalancerStats loadBalancerStats = balancer.getLoadBalancerStats();
        assertNotNull(loadBalancerStats);
        List<Server> servers = new ArrayList<Server>();

        servers.add(createServer(1, "a"));
        servers.add(createServer(2, "a"));
        servers.add(createServer(1, "b"));
        servers.add(createServer(2, "b"));
        servers.add(createServer(1, "c"));
        servers.add(createServer(2, "c"));
        balancer.setServersList(servers);
        balancer.setUpServerList(servers);
        testChooseServer(balancer, "us-east-1a", "us-east-1b", "us-east-1c");

        for (int i = 0; i < 3; i++) {
            loadBalancerStats.incrementSuccessiveConnectionFailureCount(getServer(1, "a"));
            loadBalancerStats.incrementSuccessiveConnectionFailureCount(getServer(2, "a"));
        }
        assertEquals(2, loadBalancerStats.getCircuitBreakerTrippedCount("us-east-1a"));
        testChooseServer(balancer, "us-east-1b", "us-east-1c");
        loadBalancerStats.incrementActiveRequestsCount(getServer(1, "b"));
        loadBalancerStats.incrementActiveRequestsCount(getServer(1, "c"));
        testChooseServer(balancer, "us-east-1b", "us-east-1c");
        loadBalancerStats.decrementActiveRequestsCount(getServer(1, "b"));
        // zone a all instances are blacked out, zone c is worst in terms of load
        testChooseServer(balancer, "us-east-1b");
    }

    @Test
    public void testNonZoneOverride() {
        EurekaZoneAwareLoadBalancer balancer = new EurekaZoneAwareLoadBalancer();
        balancer.init();
        LoadBalancerStats loadBalancerStats = balancer.getLoadBalancerStats();
        assertNotNull(loadBalancerStats);
        List<Server> servers = new ArrayList<Server>();

        for (int i = 1; i <= 11; i++) {
            servers.add(createServer(i, "a"));
            servers.add(createServer(i, "b"));
            servers.add(createServer(i, "c"));
        }
        balancer.setServersList(servers);
        balancer.setUpServerList(servers);
        // should not triggering zone override
        loadBalancerStats.incrementActiveRequestsCount(getServer(1, "b"));
        loadBalancerStats.incrementActiveRequestsCount(getServer(1, "c"));

        testChooseServer(balancer, "us-east-1a", "us-east-1b", "us-east-1c");

        loadBalancerStats.incrementActiveRequestsCount(getServer(1, "b"));
        loadBalancerStats.incrementActiveRequestsCount(getServer(1, "b"));

        testChooseServer(balancer, "us-east-1a", "us-east-1c");

        loadBalancerStats.incrementActiveRequestsCount(getServer(2, "c"));
        loadBalancerStats.incrementActiveRequestsCount(getServer(4, "c"));

        // now b and c are equally bad, no guarantee which zone will be chosen
        testChooseServer(balancer, "us-east-1a", "us-east-1b", "us-east-1c");

    }


    @Test
    public void testAvailabilityFiltering() {
        EurekaZoneAwareLoadBalancer balancer = new EurekaZoneAwareLoadBalancer();
        balancer.init();
        balancer.setRule(new AvailabilityFilteringRule());
        LoadBalancerStats loadBalancerStats = balancer.getLoadBalancerStats();
        assertNotNull(loadBalancerStats);
        List<Server> servers = new ArrayList<Server>();
        servers.add(createServer(1, "a"));
        servers.add(createServer(2, "a"));
        servers.add(createServer(1, "b"));
        servers.add(createServer(2, "b"));
        servers.add(createServer(1, "c"));
        servers.add(createServer(2, "c"));
        balancer.setServersList(servers);
        balancer.setUpServerList(servers);
        // cause both zone a and b outage
        for (int i = 0; i < 4; i++) {
            Server server = servers.get(i);
            for (int j = 0; j < 3; j++) {
                loadBalancerStats.getSingleServerStat(server).incrementSuccessiveConnectionFailureCount();
            }
        }
        Set<Server> expected = new HashSet<Server>();
        expected.add(createServer(1, "c"));
        expected.add(createServer(2, "c"));
        Set<Server> result = new HashSet<Server>();
        for (int i = 0; i < 20; i++) {
            Server server = balancer.chooseServer(null);
            result.add(server);
        }
        assertEquals(expected, result);
        // cause one server in c circuit tripped
        for (int i = 0; i < 3; i++) {
            loadBalancerStats.getSingleServerStat(servers.get(4)).incrementSuccessiveConnectionFailureCount();
        }
        // should only use the other server in zone c
        for (int i = 0; i < 20; i++) {
            Server server = balancer.chooseServer(null);
            assertEquals(servers.get(5), server);
        }
        // now every server is tripped
        for (int i = 0; i < 3; i++) {
            loadBalancerStats.getSingleServerStat(servers.get(5)).incrementSuccessiveConnectionFailureCount();
        }
        expected = new HashSet<Server>(servers);
        result = new HashSet<Server>();
        for (int i = 0; i < 20; i++) {
            Server server = balancer.chooseServer(null);
            if (server == null) {
                fail("Unexpected null server");
            }
            result.add(server);
        }
        assertEquals(expected, result);
        servers = new ArrayList<Server>();
        servers.add(createServer(1, "a"));
        servers.add(createServer(2, "a"));
        servers.add(createServer(1, "b"));
        servers.add(createServer(2, "b"));
        balancer.setServersList(servers);
        assertTrue(balancer.getLoadBalancer("us-east-1c").getAllServers().isEmpty());
        AvailabilityFilteringRule rule = (AvailabilityFilteringRule) balancer.getLoadBalancer("us-east-1c").getRule();
        assertEquals(0, rule.getAvailableServersCount());
    }

    @Test
    public void testConstruction() {
        ConfigurationManager.getConfigInstance().setProperty("mylb.ribbon.NFLoadBalancerRuleClassName", RandomRule.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("mylb.ribbon.NFLoadBalancerClassName", EurekaZoneAwareLoadBalancer.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("mylb.ribbon.DeploymentContextBasedVipAddresses", "dummy");

        try {
            EurekaZoneAwareLoadBalancer lb = (EurekaZoneAwareLoadBalancer) ClientFactory.getNamedLoadBalancer("mylb");
            assertTrue(lb.getLoadBalancer("myzone").getRule() instanceof RandomRule);
        } finally {
            ConfigurationManager.getConfigInstance().clearProperty("mylb.ribbon.NFLoadBalancerRuleClassName");
            ConfigurationManager.getConfigInstance().clearProperty("mylb.ribbon.NFLoadBalancerClassName");
            ConfigurationManager.getConfigInstance().clearProperty("mylb.ribbon.DeploymentContextBasedVipAddresses");
        }
    }

    @Test
    public void testActiveConnectionsLimit() {
        ConfigurationManager.getConfigInstance().clearProperty("niws.loadbalancer.serverStats.activeRequestsCount.effectiveWindowSeconds");

        ConfigurationManager.getConfigInstance().setProperty("testlb.ribbon.ActiveConnectionsLimit", 1);
        ConfigurationManager.getConfigInstance().setProperty("testlb.ribbon.NFLoadBalancerClassName", EurekaZoneAwareLoadBalancer.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("testlb.ribbon.DeploymentContextBasedVipAddresses", vipAddress);

        try {
            EurekaZoneAwareLoadBalancer balancer = (EurekaZoneAwareLoadBalancer) ClientFactory.getNamedLoadBalancer("testlb");
            LoadBalancerStats loadBalancerStats = balancer.getLoadBalancerStats();
            assertNotNull(loadBalancerStats);
            List<Server> servers = new ArrayList<Server>();
            servers.add(createServer(1, "a"));
            servers.add(createServer(2, "a"));
            servers.add(createServer(3, "a"));
            servers.add(createServer(4, "a"));
            servers.add(createServer(5, "a"));
            servers.add(createServer(6, "a"));
            servers.add(createServer(1, "b"));
            servers.add(createServer(2, "b"));
            servers.add(createServer(3, "b"));
            servers.add(createServer(4, "b"));
            servers.add(createServer(5, "b"));
            servers.add(createServer(6, "b"));
            servers.add(createServer(7, "b"));
            servers.add(createServer(8, "b"));
            balancer.setServersList(servers);
            balancer.setUpServerList(servers);
            // cause both zone a and b outage
            Server server = servers.get(0);
            loadBalancerStats.getSingleServerStat(server).incrementActiveRequestsCount();
            server = servers.get(8);
            loadBalancerStats.getSingleServerStat(server).incrementActiveRequestsCount();

            server = servers.get(servers.size() - 1);
            for (int j = 0; j < 3; j++) {
                loadBalancerStats.getSingleServerStat(server).incrementSuccessiveConnectionFailureCount();
            }

            Set<Server> expected = new HashSet<Server>();
            expected.add(createServer(2, "a"));
            expected.add(createServer(3, "a"));
            expected.add(createServer(4, "a"));
            expected.add(createServer(5, "a"));
            expected.add(createServer(6, "a"));
            expected.add(createServer(1, "b"));
            expected.add(createServer(2, "b"));
            expected.add(createServer(4, "b"));
            expected.add(createServer(5, "b"));
            expected.add(createServer(6, "b"));
            expected.add(createServer(7, "b"));
            AvailabilityFilteringRule rule = (AvailabilityFilteringRule) balancer.getRule();
            assertEquals(expected.size(), rule.getAvailableServersCount());
            AvailabilityFilteringRule rule1 = (AvailabilityFilteringRule) balancer.getLoadBalancer("us-east-1a").getRule();
            assertEquals(5, rule1.getAvailableServersCount());
            AvailabilityFilteringRule rule2 = (AvailabilityFilteringRule) balancer.getLoadBalancer("us-east-1b").getRule();
            assertEquals(6, rule2.getAvailableServersCount());

            Set<Server> result = new HashSet<Server>();
            for (int i = 0; i < 100; i++) {
                result.add(balancer.chooseServer(null));
            }
            assertEquals(expected, result);
        } finally {
            ConfigurationManager.getConfigInstance().clearProperty("testlb.ribbon.ActiveConnectionsLimit");
            ConfigurationManager.getConfigInstance().clearProperty("testlb.ribbon.NFLoadBalancerClassName");
            ConfigurationManager.getConfigInstance().clearProperty("testlb.ribbon.DeploymentContextBasedVipAddresses");
        }
    }
}
