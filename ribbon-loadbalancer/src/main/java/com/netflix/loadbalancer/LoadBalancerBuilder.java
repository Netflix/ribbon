package com.netflix.loadbalancer;

import com.netflix.client.ClientFactory;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.ClientConfigFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

import java.util.List;

public class LoadBalancerBuilder<T extends Server> {
    
    private IClientConfig config = ClientConfigFactory.findDefaultConfigFactory().newConfig();
    private ServerListFilter serverListFilter;
    private IRule rule;
    private IPing ping = new DummyPing();
    private ServerList serverListImpl;
    private ServerListUpdater serverListUpdater;
    private IClientConfigAware.Factory factory = ClientFactory::instantiateInstanceWithClientConfig;
    
    private LoadBalancerBuilder() {
    }
    
    public static <T extends Server> LoadBalancerBuilder<T> newBuilder() {
        return new LoadBalancerBuilder<T>();
    }

    public LoadBalancerBuilder<T> withFactory(IClientConfigAware.Factory factory) {
        this.factory = factory;
        return this;
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

    public LoadBalancerBuilder<T> withServerListUpdater(ServerListUpdater serverListUpdater) {
        this.serverListUpdater = serverListUpdater;
        return this;
    }

    public BaseLoadBalancer buildFixedServerListLoadBalancer(List<T> servers) {
        if (rule == null) {
            rule = createRuleFromConfig(config, factory);
        }
        BaseLoadBalancer lb = new BaseLoadBalancer(config, rule, ping);
        lb.setServersList(servers);
        return lb;
    }
    
    private static IRule createRuleFromConfig(IClientConfig config, IClientConfigAware.Factory factory) {
        String ruleClassName = config.getOrDefault(IClientConfigKey.Keys.NFLoadBalancerRuleClassName);
        if (ruleClassName == null) {
            throw new IllegalArgumentException("NFLoadBalancerRuleClassName is not specified in the config");
        }
        IRule rule;
        try {
            rule = (IRule) factory.create(ruleClassName, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return rule;
    }

    private static ServerListUpdater createServerListUpdaterFromConfig(IClientConfig config, IClientConfigAware.Factory factory) {
        String serverListUpdaterClassName = config.getOrDefault(IClientConfigKey.Keys.ServerListUpdaterClassName);
        if (serverListUpdaterClassName == null) {
            throw new IllegalArgumentException("NIWSServerListClassName is not specified in the config");
        }
        ServerListUpdater updater;
        try {
            updater = (ServerListUpdater) factory.create(serverListUpdaterClassName, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return updater;
    }
    
    private static ServerList<Server> createServerListFromConfig(IClientConfig config, IClientConfigAware.Factory factory) {
        String serverListClassName = config.get(IClientConfigKey.Keys.NIWSServerListClassName);
        if (serverListClassName == null) {
            throw new IllegalArgumentException("NIWSServerListClassName is not specified in the config");
        }
        ServerList<Server> list;
        try {
            list = (ServerList<Server>) factory.create(serverListClassName, config);
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
            serverListImpl = createServerListFromConfig(config, factory);
        }
        if (rule == null) {
            rule = createRuleFromConfig(config, factory);
        }
        return new ZoneAwareLoadBalancer<T>(config, rule, ping, serverListImpl, serverListFilter);
    }

    /**
     * Build a {@link ZoneAwareLoadBalancer} with a dynamic {@link ServerList} and an {@link IRule} and a {@link ServerListUpdater}.
     *
     * The {@link ServerList} can be either set in the {@link #withDynamicServerList(ServerList)} or in the {@link IClientConfig}
     * using {@link CommonClientConfigKey#NIWSServerListClassName}.
     * The {@link IRule} can be either set by {@link #withRule(IRule)} or in the {@link IClientConfig} using
     * {@link CommonClientConfigKey#NFLoadBalancerRuleClassName}.
     * The {@link ServerListUpdater} can be either set by {@link #withServerListUpdater(ServerListUpdater)} or
     * in the {@link IClientConfig} using {@link CommonClientConfigKey#ServerListUpdaterClassName}.
     */
    public ZoneAwareLoadBalancer<T> buildDynamicServerListLoadBalancerWithUpdater() {
        if (serverListImpl == null) {
            serverListImpl = createServerListFromConfig(config, factory);
        }
        if (rule == null) {
            rule = createRuleFromConfig(config, factory);
        }
        if (serverListUpdater == null) {
            serverListUpdater = createServerListUpdaterFromConfig(config, factory);
        }
        return new ZoneAwareLoadBalancer<T>(config, rule, ping, serverListImpl, serverListFilter, serverListUpdater);
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
            lb = (ILoadBalancer) factory.create(loadBalancerClassName, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return lb;
    }
}
