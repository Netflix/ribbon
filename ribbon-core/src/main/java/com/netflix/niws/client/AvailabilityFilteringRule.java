package com.netflix.niws.client;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.NFLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;

public class AvailabilityFilteringRule extends NIWSRoundRobinRule {
    
    private static final DynamicBooleanProperty CIRCUIT_BREAKER_FILTERING =
        DynamicPropertyFactory.getInstance().getBooleanProperty("niws.loadbalancer.availabilityFilteringRule.filterCircuitTripped", true);

    private static final DynamicIntProperty ACTIVE_CONNECTIONS_LIMIT =
        DynamicPropertyFactory.getInstance().getIntProperty("niws.loadbalancer.availabilityFilteringRule.activeConnectionsLimit", Integer.MAX_VALUE);

        
    @Override
    public Server choose(NFLoadBalancer lb, Object key) {
        LoadBalancerStats lbStats = lb.getLoadBalancerStats();
        Server server = super.choose(lb, key);
        if (lbStats == null) {
            return server;
        }
        int count = lb.getServerList(false).size();
        while (server != null && count > 0) {
            ServerStats stats = lbStats.getSingleServerStat(server);
            if ((CIRCUIT_BREAKER_FILTERING.get() && stats.isCircuitBreakerTripped()) 
                    || stats.getActiveRequestsCount() >= ACTIVE_CONNECTIONS_LIMIT.get()) {
                // try again 
                server = super.choose(lb, key);
                count--;                
            } else {
                break;
            }
        }                
        return server;
    }
}
