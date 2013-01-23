package com.netflix.niws.client;

import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.Server;

public abstract class AbstractNIWSLoadBalancerRule implements IRule, IClientConfigAware {

    AbstractLoadBalancer lb;
    
    @Override
    public Server choose(BaseLoadBalancer lb, Object key) {
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
