package com.netflix.loadbalancer;

import java.util.List;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;

public class LoadBalancerBuilder<T extends Server> {
    
    private IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
    private RetryHandler errorHandler = RetryHandler.DEFAULT;
    private ServerListFilter serverListFilter;
    private IRule rule;
    private IPing ping = new DummyPing();
    private ServerList serverListImpl;
    
    
    private LoadBalancerBuilder() {
    }
    
    public static <T extends Server> LoadBalancerBuilder<T> newBuilder() {
        return new LoadBalancerBuilder<T>();
    }
    
    public LoadBalancerBuilder<T> withClientConfig(IClientConfig config) {
        this.config = config;
        return this;
    }
    
    public LoadBalancerBuilder<T> withLoadBalancerExecutorRetryHandler(RetryHandler errorHandler) {
        this.errorHandler = errorHandler;
        return this;
    }

    public LoadBalancerBuilder<T> withRule(IRule rule) {
        this.rule = rule;
        return this;
    }
    
    public LoadBalancerBuilder<T> withPing(IPing ping) {
        this.ping = ping;
        return this;
    }
    
    public LoadBalancerBuilder<T> withDynamicServerList(ServerList<T> serverListImpl) {
        this.serverListImpl = serverListImpl;
        return this;
    }
    
    public LoadBalancerBuilder<T> withServerListFilter(ServerListFilter<T> serverListFilter) {
        this.serverListFilter = serverListFilter;
        return this;
    }

    public BaseLoadBalancer buildFixedServerListLoadBalancer(List<T> servers) {
        if (rule == null) {
            rule = createDefaultRule(config);
        }
        BaseLoadBalancer lb = new BaseLoadBalancer(config, rule, ping);
        lb.setServersList(servers);
        return lb;
    }
    
    private static IRule createDefaultRule(IClientConfig config) {
        AvailabilityFilteringRule rule = new AvailabilityFilteringRule();
        rule.initWithNiwsConfig(config);
        return rule;
    }
    
    private static ServerList<Server> createDefaultServerList(IClientConfig config) {
        ConfigurationBasedServerList list = new ConfigurationBasedServerList();
        list.initWithNiwsConfig(config);
        return list;
    }
    
    public ZoneAwareLoadBalancer<T> buildDynamicServerListLoadBalancer() {
        if (serverListImpl == null) {
            serverListImpl = createDefaultServerList(config);
        }
        if (rule == null) {
            rule = createDefaultRule(config);
        }
        return new ZoneAwareLoadBalancer<T>(config, rule, ping, serverListImpl, serverListFilter);
    }
    
    public LoadBalancerExecutor buildDynamicServerListLoadBalancerExecutor() {
        ZoneAwareLoadBalancer<T> lb = buildDynamicServerListLoadBalancer();
        LoadBalancerExecutor executor = new LoadBalancerExecutor(lb, config, errorHandler);
        return executor;
    }
    
    public LoadBalancerExecutor buildFixedServerListLoadBalancerExecutor(List<T> servers) {
        BaseLoadBalancer lb = buildFixedServerListLoadBalancer(servers);
        LoadBalancerExecutor executor = new LoadBalancerExecutor(lb, config, errorHandler);
        return executor;        
    }
}
