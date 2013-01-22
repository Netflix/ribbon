package com.netflix.niws.client;

import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerListFilter;

/**
 * Class that is responsible to Filter out list of servers from the ones 
 * currently available in the Load Balancer
 * @author stonse
 *
 * @param <T>
 */
public abstract class AbstractNIWSServerListFilter<T extends Server> implements ServerListFilter<T> {

    private volatile LoadBalancerStats stats;
    
    public void setLoadBalancerStats(LoadBalancerStats stats) {
        this.stats = stats;
    }
    
    public LoadBalancerStats getLoadBalancerStats() {
        return stats;
    }

}
