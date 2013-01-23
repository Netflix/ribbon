package com.netflix.loadbalancer;

import java.util.List;

import com.netflix.niws.client.NiwsClientConfig;
import com.netflix.niws.client.IClientConfigAware;

/**
 * AbstractLoadBalancer that captures all the operations and methods needed 
 * from a load balancer point of view.
 * 
 * @author stonse
 *
 */
public abstract class AbstractLoadBalancer implements ILoadBalancer {
    
    public enum ServerGroup{
        ALL,
        STATUS_UP,
        STATUS_NOT_UP        
    }
        
    /**
     * delegate to {@link #chooseServer(Object)} with parameter null.
     */
    public Server chooseServer() {
    	return chooseServer(null);
    }

    
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
}
