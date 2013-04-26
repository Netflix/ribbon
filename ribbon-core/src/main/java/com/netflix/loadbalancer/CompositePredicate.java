
/*
 *
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.loadbalancer;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;

/**
 * A predicate that is composed from one or more predicates in "AND" relationship.
 * It also has the functionality of "fallback" to one of more different predicates.
 * If the primary predicate yield too few filtered servers from the {@link #getEligibleServers(List, Object)}
 * API, it will try the fallback predicates one by one, until the number of filtered servers
 * exceeds certain number threshold or percentage threshold. 
 * 
 * @author awang
 *
 */
public class CompositePredicate extends AbstractServerPredicate {

    private AbstractServerPredicate delegate;
    
    private List<AbstractServerPredicate> fallbacks = Lists.newArrayList();
        
    private int minimalFilteredServers = 1;
    
    private float minimalFilteredPercentage = 0;    
    
    @Override
    public boolean apply(@Nullable PredicateKey input) {
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
            Predicate<PredicateKey> chain = Predicates.<PredicateKey>and(primaryPredicates);
            toBuild.delegate =  AbstractServerPredicate.ofKeyPredicate(chain);                
        }

        public Builder addFallbackPredicate(AbstractServerPredicate fallback) {
            toBuild.fallbacks.add(fallback);
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

    /**
     * Get the filtered servers from primary predicate, and if the number of the filtered servers
     * are not enough, trying the fallback predicates  
     */
    @Override
    public List<Server> getEligibleServers(List<Server> servers, Object loadBalancerKey) {
        List<Server> result = super.getEligibleServers(servers, loadBalancerKey);
        Iterator<AbstractServerPredicate> i = fallbacks.iterator();
        while (!(result.size() >= minimalFilteredServers && result.size() > (int) (servers.size() * minimalFilteredPercentage))
                && i.hasNext()) {
            AbstractServerPredicate predicate = i.next();
            result = predicate.getEligibleServers(servers, loadBalancerKey);
        }
        return result;
    }
}
