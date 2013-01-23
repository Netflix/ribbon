package com.netflix.loadbalancer;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.niws.client.IClientConfig;

/**
 * A noOp Loadbalancer
 * i.e. doesnt do anything "loadbalancer like"
 * @author stonse
 *
 */
public class NoOpLoadBalancer extends AbstractLoadBalancer {

    static final Logger  logger = LoggerFactory.getLogger(NoOpLoadBalancer.class);
    
    
    @Override
    public void addServers(List<Server> newServers) {
        // TODO Auto-generated method stub
        logger.info("addServers to NoOpLoadBalancer ignored");
    }

    @Override
    public Server chooseServer(Object key) {       
        return null;
    }

    @Override
    public LoadBalancerStats getLoadBalancerStats() {        
        return null;
    }

    
    @Override
    public List<Server> getServerList(ServerGroup serverGroup) {     
        return Collections.emptyList();
    }

    @Override
    public void markServerDown(Server server) {
        logger.info("markServerDown to NoOpLoadBalancer ignored");
    }    
}
