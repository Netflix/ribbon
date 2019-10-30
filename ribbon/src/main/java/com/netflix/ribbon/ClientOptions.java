/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API to construct Ribbon client options to be used by {@link ResourceGroup}
 * 
 * @author awang
 *
 */
public final class ClientOptions {
    
    private Map<IClientConfigKey<?>, Object> options;
    
    private ClientOptions() {
        options = new ConcurrentHashMap<>();
    }
    
    public static ClientOptions create() {
        return new ClientOptions();
    }

    public static ClientOptions from(IClientConfig config) {
        ClientOptions options = new ClientOptions();
        for (IClientConfigKey key: IClientConfigKey.Keys.keys()) {
            Object value = config.get(key);
            if (value != null) {
                options.options.put(key, value);
            }
        }
        return options;
    }

    public ClientOptions withDiscoveryServiceIdentifier(String identifier) {
        options.put(IClientConfigKey.Keys.DeploymentContextBasedVipAddresses, identifier);
        return this;
    }
    
    public ClientOptions withConfigurationBasedServerList(String serverList) {
        options.put(IClientConfigKey.Keys.ListOfServers, serverList);
        return this;
    }
        
    public ClientOptions withMaxAutoRetries(int value) {
        options.put(IClientConfigKey.Keys.MaxAutoRetries, value);
        return this;
    }

    public ClientOptions withMaxAutoRetriesNextServer(int value) {
        options.put(IClientConfigKey.Keys.MaxAutoRetriesNextServer, value);
        return this;        
    }
    
    public ClientOptions withRetryOnAllOperations(boolean value) {
        options.put(IClientConfigKey.Keys.OkToRetryOnAllOperations, value);
        return this;
    }
        
    public ClientOptions withMaxConnectionsPerHost(int value) {
        options.put(IClientConfigKey.Keys.MaxConnectionsPerHost, value);
        return this;        
    }

    public ClientOptions withMaxTotalConnections(int value) {
        options.put(IClientConfigKey.Keys.MaxTotalConnections, value);
        return this;        
    }
    
    public ClientOptions withConnectTimeout(int value) {
        options.put(IClientConfigKey.Keys.ConnectTimeout, value);
        return this;                
    }

    public ClientOptions withReadTimeout(int value) {
        options.put(IClientConfigKey.Keys.ReadTimeout, value);
        return this;        
    }

    public ClientOptions withFollowRedirects(boolean value) {
        options.put(IClientConfigKey.Keys.FollowRedirects, value);
        return this;                
    }
            
    public ClientOptions withConnectionPoolIdleEvictTimeMilliseconds(int value) {
        options.put(IClientConfigKey.Keys.ConnIdleEvictTimeMilliSeconds, value);
        return this;                        
    }
    
    public ClientOptions withLoadBalancerEnabled(boolean value) {
        options.put(IClientConfigKey.Keys.InitializeNFLoadBalancer, value);
        return this;                                
    }

    Map<IClientConfigKey<?>, Object> getOptions() {
        return options;
    }

}
