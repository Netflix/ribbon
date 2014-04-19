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
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;
import rx.Observable;

import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ClientObservableProvider;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerExecutor;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerListChangeListener;
import com.netflix.loadbalancer.ServerStats;

public class NettyHttpLoadBalancingClient extends NettyHttpClient {

    private LoadBalancerExecutor lbExecutor;
    
    public NettyHttpLoadBalancingClient(ILoadBalancer lb, IClientConfig config) {
        this(lb, config, new NettyHttpLoadBalancerErrorHandler(config));
    }
        
    public NettyHttpLoadBalancingClient(ILoadBalancer lb, IClientConfig config, RetryHandler errorHandler) {
        super(config);
        lbExecutor = new LoadBalancerExecutor(lb, config);
        lbExecutor.setErrorHandler(errorHandler);
        addLoadBalancerListener();
    }

    private RequestSpecificRetryHandler getRequestRetryHandler(HttpClientRequest<?> request, IClientConfig requestConfig) {
        boolean okToRetryOnAllErrors = request.getMethod().equals(HttpMethod.GET);
        return new RequestSpecificRetryHandler(true, okToRetryOnAllErrors, lbExecutor.getErrorHandler(), requestConfig);
    }
        
    private void addLoadBalancerListener() {
        ILoadBalancer lb = lbExecutor.getLoadBalancer();
        if (!(lb instanceof DynamicServerListLoadBalancer)) {
            return;
        }
        ((DynamicServerListLoadBalancer) lb).addServerListChangeListener(new ServerListChangeListener() {
            @Override
            public void serverListChanged(List<Server> oldList, List<Server> newList) {
                Map<Server, HttpClient> clients = getCurrentHttpClients();
                for (Server server: clients.keySet()) {
                    if (!newList.contains(server)) {
                        // this server is no longer in UP status
                        removeClient(server);
                    }
                }
                int oldSize = oldList.size();
                int newSize = newList.size();
                if (oldSize != newSize) {
                    int maxTotalConnections = getMaxTotalConnections() * newSize / oldSize;
                    setMaxTotalConnections(maxTotalConnections);
                }
            }
        });
    }
    
    public <I> Observable<HttpClientResponse<ByteBuf>> submitToLoadBalancer(final HttpClientRequest<I> request) {
        return submitToLoadBalancer(request, null, null);
    }
    
    public <I> Observable<HttpClientResponse<ByteBuf>> submitToLoadBalancer(final HttpClientRequest<I> request, final RetryHandler errorHandler, final IClientConfig requestConfig) {
        final RepeatableContentHttpRequest<I> repeatableRequest = getRepeatableRequest(request);
        final RetryHandler retryHandler = (errorHandler == null) ? getRequestRetryHandler(request, requestConfig) : errorHandler;
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<HttpClientResponse<ByteBuf>>() {
            @Override
            public Observable<HttpClientResponse<ByteBuf>> getObservableForEndpoint(
                    Server server) {
                return submit(server.getHost(), server.getPort(), repeatableRequest, requestConfig);
            }
        }, retryHandler);
    }
    
    public ServerStats getServerStats(Server server) {
        return lbExecutor.getServerStats(server);
    }
    
    protected final void setDefaultRetryHandler(RetryHandler errorHandler) {
        lbExecutor.setErrorHandler(errorHandler);
    }
}
