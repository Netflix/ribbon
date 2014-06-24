package com.netflix.ribbonclientextensions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;

public final class ClientOptions {
    
    private Map<IClientConfigKey<?>, Object> options;
    
    private ClientOptions() {
        options = new ConcurrentHashMap<IClientConfigKey<?>, Object>();
    }
    
    public static ClientOptions create() {
        return new ClientOptions();
    }
    
    public ClientOptions useEurekaDynamicServerList(String vipAddress) {
        options.put(IClientConfigKey.CommonKeys.NIWSServerListClassName, DiscoveryEnabledNIWSServerList.class.getName());
        options.put(IClientConfigKey.CommonKeys.DeploymentContextBasedVipAddresses, vipAddress);
        return this;
    }
    
    public ClientOptions useConfigurationBasedServerList(String serverList) {
        options.put(IClientConfigKey.CommonKeys.ListOfServers, serverList);
        return this;
    }
        
    public ClientOptions withMaxAutoRetries(int value) {
        options.put(IClientConfigKey.CommonKeys.MaxAutoRetries, value);
        return this;
    }

    public ClientOptions withMaxAutoRetriesNextServer(int value) {
        options.put(IClientConfigKey.CommonKeys.MaxAutoRetriesNextServer, value);
        return this;        
    }
    
    public ClientOptions withRetryOnAllOperations(boolean value) {
        options.put(IClientConfigKey.CommonKeys.OkToRetryOnAllOperations, value);
        return this;
    }
        
    public ClientOptions withMaxConnectionsPerHost(int value) {
        options.put(IClientConfigKey.CommonKeys.MaxConnectionsPerHost, value);
        return this;        
    }

    public ClientOptions withMaxTotalConnections(int value) {
        options.put(IClientConfigKey.CommonKeys.MaxTotalConnections, value);
        return this;        
    }
    
    public ClientOptions withConnectTimeout(int value) {
        options.put(IClientConfigKey.CommonKeys.ConnectTimeout, value);
        return this;                
    }

    public ClientOptions withReadTimeout(int value) {
        options.put(IClientConfigKey.CommonKeys.ReadTimeout, value);
        return this;        
    }

    public ClientOptions withFollowRedirects(boolean value) {
        options.put(IClientConfigKey.CommonKeys.FollowRedirects, value);
        return this;                
    }
            
    public ClientOptions withConnectionPoolIdleEvictTimeMilliseconds(int value) {
        options.put(IClientConfigKey.CommonKeys.ConnIdleEvictTimeMilliSeconds, value);
        return this;                        
    }
    
    public ClientOptions withLoadBalancerEnabled(boolean value) {
        options.put(IClientConfigKey.CommonKeys.InitializeNFLoadBalancer, value);
        return this;                                
    }
    
    Map<IClientConfigKey<?>, Object> getOptions() {
        return options;
    }

}
