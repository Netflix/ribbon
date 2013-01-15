package com.netflix.niws.client;

import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.NFLoadBalancer;
import com.netflix.loadbalancer.ResponseTimeWeightedRule;
import com.netflix.loadbalancer.Server;

/**
 * This class essentially contains the ResponseTimeWeightedRule class defined in the loadbalancer package
 * @author stonse
 *
 */
public class NIWSWeightedResponseTimeRule extends AbstractNIWSLoadBalancerRule{

    ResponseTimeWeightedRule rule = new ResponseTimeWeightedRule();
    
    @Override
    public void initWithNiwsConfig(
            NiwsClientConfig niwsClientConfig) {
       rule = new ResponseTimeWeightedRule();       
    }

    @Override
    //TODO(stonse): Consider refactoring this so that we dont need to override this
    public void setLoadBalancer(AbstractLoadBalancer lb){
       super.setLoadBalancer(lb);
       rule.setLoadBalancer(lb);// set it for the contained Rule class
       rule.initialize(lb);
    }
    
    @Override
    public Server choose(NFLoadBalancer lb, Object key) {       
        if (rule!=null){
            return rule.choose(lb, key);
        }else{
            throw new IllegalArgumentException("This class has not been initialized with the RoundRobinRule class");
        }
    }

}
