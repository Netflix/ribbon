package com.netflix.loadbalancer;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;

public class CompositePredicate extends AbstractServerPredicate {

    private AbstractServerPredicate delegate;
    
    private List<AbstractServerPredicate> fallbacks = Lists.newArrayList();
    
    private boolean allowEmptyList = true;
    
    private int minimalFilteredServers = 1;
    
    private float minimalFilteredPercentage = 0;
    
    private AbstractServerPredicate alwaysTrue = new AbstractServerPredicate() {        
        @Override
        public boolean apply(@Nullable Key input) {
            return true;
        }
    };

    
    @Override
    public boolean apply(@Nullable Key input) {
        return delegate.apply(input);
    }

    
    public static class Builder {
        
        private CompositePredicate toBuild;
        
        Builder(AbstractServerPredicate primaryPredicate) {
            toBuild = new CompositePredicate();    
            toBuild.delegate = primaryPredicate;                    
        }

        Builder(AbstractServerPredicate ...primaryPredicates) {
            toBuild = new CompositePredicate();
            Predicate<Key> chain = Predicates.<Key>and(primaryPredicates);
            toBuild.delegate =  AbstractServerPredicate.ofKeyPredicate(chain);                
        }

        public Builder addFallbackPredicate(AbstractServerPredicate fallback) {
            toBuild.fallbacks.add(fallback);
            return this;
        }
        
        public Builder setAllowEmptyFilteredList(boolean value) {
            toBuild.allowEmptyList = value;
            return this;
        }
        
        public Builder setFallbackThresholdAsMinimalFilteredNumberOfServers(int number) {
            toBuild.minimalFilteredServers = number;
            return this;
        }
        
        public Builder setFallbackThresholdAsMinimalFilteredPercentage(float percent) {
            toBuild.minimalFilteredPercentage = percent;
            return this;
        }
        
        public CompositePredicate build() {
            return toBuild;
        }
    }
    
    public static Builder withPredicates(AbstractServerPredicate ...primaryPredicates) {
        return new Builder(primaryPredicates);
    }

    public static Builder withPredicate(AbstractServerPredicate primaryPredicate) {
        return new Builder(primaryPredicate);
    }

    @Override
    public List<Server> getEligibleServers(List<Server> servers, Object loadBalancerKey) {
        List<Server> result = super.getEligibleServers(servers, loadBalancerKey);
        if (result.size() >= minimalFilteredServers || result.size() >= servers.size() * minimalFilteredPercentage) {
            return result;
        } 
        for (AbstractServerPredicate predicate: fallbacks) {
            result = predicate.getEligibleServers(servers, loadBalancerKey);
            if (result.size() > 0) {
                return result;
            }
        }
        if (!allowEmptyList) {
            return alwaysTrue.getEligibleServers(servers, loadBalancerKey);
        }
        return Lists.newArrayList();
    }
}
