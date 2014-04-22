package com.netflix.niws.loadbalancer;

import static org.junit.Assert.*;

import org.junit.Test;

import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.ZoneAffinityServerListFilter;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;

public class LBBuilderTest {
    @Test
    public void testBuilder() {
        ZoneAwareLoadBalancer<DiscoveryEnabledServer> lb = LoadBalancerBuilder.<DiscoveryEnabledServer>newBuilder()
                .withDynamicServerList(new DiscoveryEnabledNIWSServerList())
                .withRule(new AvailabilityFilteringRule())
                .withServerListFilter(new ZoneAffinityServerListFilter<DiscoveryEnabledServer>())
                .buildDynamicServerListLoadBalancer();
        assertNotNull(lb);
    }

}
