package com.netflix.niws.client;

import com.netflix.loadbalancer.NFLoadBalancer;

public abstract class AbstractNIWSLoadBalancer extends NFLoadBalancer implements NiwsClientConfigAware{
    
    
    AbstractNIWSLoadBalancer(){
        super();
    }
    
  
}
