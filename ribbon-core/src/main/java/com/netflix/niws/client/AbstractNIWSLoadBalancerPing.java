package com.netflix.niws.client;

import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.Server;

public abstract class AbstractNIWSLoadBalancerPing implements IPing, NiwsClientConfigAware{

    AbstractLoadBalancer lb;
    
    @Override
    public boolean isAlive(Server server) {
        return true;
    }
    
    public void setLoadBalancer(AbstractLoadBalancer lb){
        this.lb = lb;
    }
    
    public AbstractLoadBalancer getLoadBalancer(){
        return lb;
    }

}
