package com.netflix.loadbalancer;

import org.awaitility.core.ThrowingRunnable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WeightedResponseTimeRuleTest {

    private static final Object KEY = "key";

    private AbstractLoadBalancer loadBalancer;
    private WeightedResponseTimeRule rule;

    @Before
    public void setUp() throws Exception {
        rule = new WeightedResponseTimeRule();
        loadBalancer = mock(AbstractLoadBalancer.class);
        setupLoadBalancer(asList(server("first"), server("second"), server("third")));
        rule.setLoadBalancer(loadBalancer);
    }

    @After
    public void tearDown() throws Exception {
        rule.shutdown();
    }

    @Test
    public void shouldNotFailWithIndexOutOfBoundExceptionWhenChoosingServerWhenNumberOfServersIsDecreased() throws Exception {
        waitUntilWeightsAreCalculated();

        setupLoadBalancer(singletonList(server("other")));

        Server chosen = rule.choose(loadBalancer, KEY);

        assertNotNull(chosen);
    }

    private void waitUntilWeightsAreCalculated() {
        await().untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                List<Double> weights = rule.getAccumulatedWeights();
                assertNotEquals(weights.size(), 0);
            }
        });
    }

    private AbstractLoadBalancer setupLoadBalancer(List<Server> servers) {
        LoadBalancerStats loadBalancerStats = getLoadBalancerStats(servers);
        when(loadBalancer.getLoadBalancerStats()).thenReturn(loadBalancerStats);
        when(loadBalancer.getReachableServers()).thenReturn(servers);
        when(loadBalancer.getAllServers()).thenReturn(servers);
        return loadBalancer;
    }

    private LoadBalancerStats getLoadBalancerStats(List<Server> servers) {
        LoadBalancerStats stats = mock(LoadBalancerStats.class);
        // initialize first server with maximum response time
        // so that we could reproduce issue with decreased number of servers in loadbalancer
        int responseTimeMax = servers.size() * 100;
        for (Server server : servers) {
            ServerStats s1 = statsWithResponseTimeAverage(responseTimeMax);
            when(stats.getSingleServerStat(server)).thenReturn(s1);
            responseTimeMax -= 100;
        }
        return stats;
    }

    private ServerStats statsWithResponseTimeAverage(double responseTimeAverage) {
        ServerStats serverStats = mock(ServerStats.class);
        when(serverStats.getResponseTimeAvg()).thenReturn(responseTimeAverage);
        return serverStats;
    }

    private Server server(String id) {
        Server server = new Server(id);
        server.setAlive(true);
        return server;
    }
}