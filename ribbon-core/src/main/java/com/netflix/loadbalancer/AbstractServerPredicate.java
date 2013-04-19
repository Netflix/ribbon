package com.netflix.loadbalancer;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.netflix.client.config.IClientConfig;

public abstract class AbstractServerPredicate implements ILoadBalancerPredicate {
    
    protected IRule rule;
    private volatile LoadBalancerStats lbStats;
    
    private final Random random = new Random();
    
    private final AtomicInteger nextIndex = new AtomicInteger();
            
    public AbstractServerPredicate() {
        
    }
    
    public AbstractServerPredicate(IRule rule) {
        this.rule = rule;
    }
    
    public AbstractServerPredicate(IRule rule, IClientConfig clientConfig) {
        this.rule = rule;
    }
    
    public AbstractServerPredicate(LoadBalancerStats lbStats, IClientConfig clientConfig) {
        this.lbStats = lbStats;
    }
    
    protected LoadBalancerStats getLBStats() {
        if (lbStats != null) {
            return lbStats;
        } else if (rule != null) {
            ILoadBalancer lb = rule.getLoadBalancer();
            if (lb instanceof AbstractLoadBalancer) {
                LoadBalancerStats stats =  ((AbstractLoadBalancer) lb).getLoadBalancerStats();
                setLoadBalancerStats(stats);
                return stats;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
    
    public void setLoadBalancerStats(LoadBalancerStats stats) {
        this.lbStats = stats;
    }
    
    public Predicate<Server> getServerOnlyPredicate() {
        return ServerOnlyPredicateAdapter.of(this);
    }
    
    public List<Server> getEligibleServers(List<Server> servers) {
        return getEligibleServers(servers, null);
    }
    
    public List<Server> getEligibleServers(List<Server> servers, Object loadBalancerKey) {
        if (loadBalancerKey == null) {
            return ImmutableList.copyOf(Iterables.filter(servers, this.getServerOnlyPredicate()));            
        } else {
            List<Server> results = Lists.newArrayList();
            for (Server server: servers) {
                if (this.apply(new Key(loadBalancerKey, server))) {
                    results.add(server);
                }
            }
            return results;            
        }
    }
    
    public Optional<Server> chooseRandomlyAfterFiltering(List<Server> servers) {
        List<Server> eligible = getEligibleServers(servers);
        if (eligible.size() == 0) {
            return Optional.absent();
        }
        return Optional.of(eligible.get(random.nextInt(eligible.size())));
    }
    
    public Optional<Server> chooseRoundRobinAfterFiltering(List<Server> servers) {
        List<Server> eligible = getEligibleServers(servers);
        if (eligible.size() == 0) {
            return Optional.absent();
        }
        return Optional.of(eligible.get(nextIndex.getAndIncrement() % eligible.size()));
    }
    
    public Optional<Server> chooseRandomlyAfterFiltering(List<Server> servers, Object loadBalancerKey) {
        List<Server> eligible = getEligibleServers(servers, loadBalancerKey);
        if (eligible.size() == 0) {
            return Optional.absent();
        }
        return Optional.of(eligible.get(random.nextInt(eligible.size())));
    }
    
    public Optional<Server> chooseRoundRobinAfterFiltering(List<Server> servers, Object loadBalancerKey) {
        List<Server> eligible = getEligibleServers(servers, loadBalancerKey);
        if (eligible.size() == 0) {
            return Optional.absent();
        }
        return Optional.of(eligible.get(nextIndex.getAndIncrement() % eligible.size()));
    }
    
    public static AbstractServerPredicate ofKeyPredicate(final Predicate<Key> p) {
        return new AbstractServerPredicate() {
            @Override
            @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP")
            public boolean apply(Key input) {
                Preconditions.checkNotNull(input);
                return p.apply(input);
            }            
        };        
    }
    
    public static AbstractServerPredicate ofServerPredicate(final Predicate<Server> p) {
        return new AbstractServerPredicate() {
            @Override
            @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP")
            public boolean apply(Key input) {
                Preconditions.checkNotNull(input);
                return p.apply(input.getServer());
            }            
        };        
    }
}
