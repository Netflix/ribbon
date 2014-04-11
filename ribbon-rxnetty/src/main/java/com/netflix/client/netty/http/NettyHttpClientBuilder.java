/*
 *
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.client.netty.http;

import java.util.List;

import com.google.common.base.Preconditions;
import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

/**
 * Builder for NettyHttpClient.
 * 
 * @author awang
 *
 */
public class NettyHttpClientBuilder {

    private NettyHttpClientBuilder() {}
    private IClientConfig clientConfig = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
    private ILoadBalancer lb;
    private List<Server> serverList;
    private RetryHandler errorHandler;

    public static NettyHttpClientBuilder newBuilder() {
        return new NettyHttpClientBuilder();
    }

    public NettyHttpClientBuilder withLoadBalancer(AbstractLoadBalancer lb) {
        Preconditions.checkNotNull(lb);
        this.lb = lb;
        return this;
    }

    public NettyHttpClientBuilder withClientConfig(IClientConfig clientConfig) {
        Preconditions.checkNotNull(clientConfig);
        this.clientConfig = clientConfig;
        return this;
    }
    
    public NettyHttpClientBuilder withFixedServerList(List<Server> serverList) {   
        Preconditions.checkNotNull(serverList);
        this.serverList = serverList;
        return this;
    }
    
    public NettyHttpClientBuilder withLoadBalancerRetryHandler(RetryHandler errorHandler) {
        this.errorHandler = errorHandler;
        return this;
    }

    public NettyHttpClient buildHttpClient() {
        return new NettyHttpClient(clientConfig);
    }
    
    public SSEClient buildSSEClient() {
        return new SSEClient(clientConfig);
    }
    
    
    private void createLoadBalancerFixtures() {
        if (lb == null) {
            if (serverList == null) {
                try {
                    lb = ClientFactory.registerNamedLoadBalancerFromclientConfig(clientConfig.getClientName(), clientConfig);
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // we just create a simple load balancer for static server list
                lb = new BaseLoadBalancer(clientConfig);
            }
        } else if (lb instanceof DynamicServerListLoadBalancer && serverList != null) {
            throw new IllegalArgumentException("Cannot use static server list on DynamicServerListLoadBalancer");
        }
        if (serverList != null && (lb instanceof BaseLoadBalancer)) {
            ((BaseLoadBalancer) lb).setServersList(serverList);
        }
        if (errorHandler == null) {
            errorHandler = new NettyHttpLoadBalancerErrorHandler(clientConfig);
        }
    }
    
    public NettyHttpLoadBalancingClient buildLoadBalancingClient() {
        createLoadBalancerFixtures();
        return new NettyHttpLoadBalancingClient(lb, clientConfig, errorHandler);
    }
    
    public SSELoadBalancingClient buildLoadBalancingSSEClient() {
        createLoadBalancerFixtures();
        return new SSELoadBalancingClient(lb, clientConfig, errorHandler);
    }
}
