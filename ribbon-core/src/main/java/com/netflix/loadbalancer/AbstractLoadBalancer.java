package com.netflix.loadbalancer;

import java.util.List;

import com.netflix.niws.client.NiwsClientConfig;
import com.netflix.niws.client.NiwsClientConfigAware;

/**
 * AbstractLoadBalancer that captures all the operations and methods needed 
 * from a load balancer pov
 * @author stonse
 *
 */
public abstract class AbstractLoadBalancer implements ILoadBalancer, NiwsClientConfigAware {
    
    public enum ServerGroup{
        ALL,
        STATUS_UP,
        STATUS_NOT_UP        
    }
    
    public abstract void addServers(List<Server> newServers);

    public abstract Server chooseServer(Object key);

    public abstract void markServerDown(Server server);
    
    /**
     * List of servers that this Loadbalancer knows about
     * @param availableOnly if true will only return the subset of servers that are in the list as marked as "up"
     * a null value will send all servers
     * @return
     */
    public abstract List<Server> getServerList(ServerGroup serverGroup);
    
    /**
     * Obtain LoadBalancer related Statistics
     * @return
     */
    public abstract LoadBalancerStats getLoadBalancerStats();

    @Override
    public void initWithNiwsConfig(NiwsClientConfig niwsClientConfig) {
    }
}
