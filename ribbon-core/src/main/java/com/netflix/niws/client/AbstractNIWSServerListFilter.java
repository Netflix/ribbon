package com.netflix.niws.client;

import java.util.List;

import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;

/**
 * Class that is responsible to Filter out list of servers from the ones 
 * currently available in the Load Balancer
 * @author stonse
 *
 * @param <T>
 */
public abstract class AbstractNIWSServerListFilter<T extends Server> {

    private volatile LoadBalancerStats stats;
    /**
     * Opportunity to filter out the servers based on any criteria
     * @param servers
     * @return
     */
    public abstract List<T> getFilteredListOfServers(List<T> servers);
    
    public void setLoadBalancerStats(LoadBalancerStats stats) {
        this.stats = stats;
    }
    
    public LoadBalancerStats getLoadBalancerStats() {
        return stats;
    }

}