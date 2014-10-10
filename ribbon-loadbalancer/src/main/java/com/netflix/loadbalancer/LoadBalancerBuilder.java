package com.netflix.loadbalancer;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

import java.util.List;

public class LoadBalancerBuilder<T extends Server> {
    
    private IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
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
            rule = createRuleFromConfig(config);
        }
        BaseLoadBalancer lb = new BaseLoadBalancer(config, rule, ping);
        lb.setServersList(servers);
        return lb;
    }
    
    private static IRule createRuleFromConfig(IClientConfig config) {
        String ruleClassName = config.get(IClientConfigKey.Keys.NFLoadBalancerRuleClassName);
        if (ruleClassName == null) {
            throw new IllegalArgumentException("NFLoadBalancerRuleClassName is not specified in the config");
        }
        IRule rule;
        try {
            rule = (IRule) ClientFactory.instantiateInstanceWithClientConfig(ruleClassName, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return rule;
    }
    
    private static ServerList<Server> createServerListFromConfig(IClientConfig config) {
        String serverListClassName = config.get(IClientConfigKey.Keys.NIWSServerListClassName);
        if (serverListClassName == null) {
            throw new IllegalArgumentException("NIWSServerListClassName is not specified in the config");
        }
        ServerList<Server> list;
        try {
            list = (ServerList<Server>) ClientFactory.instantiateInstanceWithClientConfig(serverListClassName, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return list;
    }
    
    /**
     * Build a {@link ZoneAwareLoadBalancer} with a dynamic {@link ServerList} and an {@link IRule}. The {@link ServerList} can be
     * either set in the {@link #withDynamicServerList(ServerList)} or in the {@link IClientConfig} using {@link CommonClientConfigKey#NIWSServerListClassName}.
     * The {@link IRule} can be either set by {@link #withRule(IRule)} or in the {@link IClientConfig} using
     * {@link CommonClientConfigKey#NFLoadBalancerRuleClassName}. 
     */
    public ZoneAwareLoadBalancer<T> buildDynamicServerListLoadBalancer() {
        if (serverListImpl == null) {
            serverListImpl = createServerListFromConfig(config);
        }
        if (rule == null) {
            rule = createRuleFromConfig(config);
        }
        return new ZoneAwareLoadBalancer<T>(config, rule, ping, serverListImpl, serverListFilter);
    }
    
    /**
     * Build a load balancer using the configuration from the {@link IClientConfig} only. It uses reflection to initialize necessary load balancer
     * components. 
     */
    public ILoadBalancer buildLoadBalancerFromConfigWithReflection() {
        String loadBalancerClassName = config.get(CommonClientConfigKey.NFLoadBalancerClassName);
        if (loadBalancerClassName == null) {
            throw new IllegalArgumentException("NFLoadBalancerClassName is not specified in the IClientConfig");
        }
        ILoadBalancer lb;
        try {
            lb = (ILoadBalancer) ClientFactory.instantiateInstanceWithClientConfig(loadBalancerClassName, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return lb;
    }
}
