package com.netflix.loadbalancer;

import com.google.common.base.Optional;

public abstract class PredicateBasedRule extends ClientConfigEnabledRoundRobinRule {
        
    public abstract AbstractServerPredicate getPredicate();
        
    @Override
    public Server choose(Object key) {
        ILoadBalancer lb = getLoadBalancer();
        Optional<Server> server = getPredicate().chooseRoundRobinAfterFiltering(lb.getServerList(false), key);
        if (server.isPresent()) {
            return server.get();
        } else {
            return super.choose(key);
        }       
    }
}
