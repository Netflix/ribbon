package com.netflix.loadbalancer;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;

public interface ILoadBalancerPredicate extends Predicate<ILoadBalancerPredicate.Key> {
    
    public static class Key {
        private Object loadBalancerKey;
        private Server server;
        
        public Key(Object loadBalancerKey, Server server) {
            super();
            this.loadBalancerKey = loadBalancerKey;
            this.server = server;
        }

        public Key(Server server) {
            this(null, server);
        }
        
        public final Optional<Object> getLoadBalancerKey() {
            if (loadBalancerKey == null) {
                return Optional.absent();
            } else {
                return Optional.of(loadBalancerKey);
            }
        }
        
        public final Server getServer() {
            return server;
        }        
    }
            
    public static class ServerOnlyPredicateAdapter {
        public static Predicate<Server> of(final ILoadBalancerPredicate predicate) {
            return new Predicate<Server>() {
                @Override
                public boolean apply(@Nullable Server input) {                    
                    return predicate.apply(new Key(input));
                }
            };
        }
    }
}
