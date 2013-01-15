package com.netflix.niws.client;

import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.NFLoadBalancer;
import com.netflix.loadbalancer.Server;

public abstract class AbstractNIWSLoadBalancerRule implements IRule, NiwsClientConfigAware {

    AbstractLoadBalancer lb;
    
    @Override
    public Server choose(NFLoadBalancer lb, Object key) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public void setLoadBalancer(AbstractLoadBalancer lb){
        this.lb = lb;
    }
    
    public AbstractLoadBalancer getLoadBalancer(){
        return lb;
    }
    

}
