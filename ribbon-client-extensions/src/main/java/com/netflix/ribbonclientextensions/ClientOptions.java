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
package com.netflix.ribbonclientextensions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.client.config.IClientConfigKey;

/**
 * API to construct Ribbon client options to be used by {@link ResourceGroup}
 * 
 * @author awang
 *
 */
public final class ClientOptions {
    
    private Map<IClientConfigKey<?>, Object> options;
    
    private ClientOptions() {
        options = new ConcurrentHashMap<IClientConfigKey<?>, Object>();
    }
    
    public static ClientOptions create() {
        return new ClientOptions();
    }
        
    public ClientOptions withDiscoveryServiceIdentifier(String identifier) {
        options.put(IClientConfigKey.CommonKeys.DeploymentContextBasedVipAddresses, identifier);
        return this;
    }
    
    public ClientOptions withConfigurationBasedServerList(String serverList) {
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
