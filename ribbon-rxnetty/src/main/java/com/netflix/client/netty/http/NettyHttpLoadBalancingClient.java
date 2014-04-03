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

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;
import rx.Observable;

import com.netflix.client.ClientObservableProvider;
import com.netflix.client.LoadBalancerExecutor;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

public class NettyHttpLoadBalancingClient extends AbstractLoadBalancingClient {

    private final NettyHttpClient delegate;

    public NettyHttpLoadBalancingClient(ILoadBalancer lb, IClientConfig config) {
        delegate = new NettyHttpClient(config);
        lbExecutor = new LoadBalancerExecutor(lb, config);
        lbExecutor.setErrorHandler(new NettyHttpLoadBalancerErrorHandler(config));
    }
        
    public NettyHttpLoadBalancingClient(ILoadBalancer lb, IClientConfig config, RetryHandler errorHandler) {
        delegate = new NettyHttpClient(config);
        lbExecutor = new LoadBalancerExecutor(lb, config);
        lbExecutor.setErrorHandler(errorHandler);
    }
        
    public <I> Observable<HttpClientResponse<ByteBuf>> submit(final HttpClientRequest<I> request) {
        return submit(request, null, null);
    }
    
    public <I> Observable<HttpClientResponse<ByteBuf>> submit(final HttpClientRequest<I> request, final RetryHandler errorHandler, final IClientConfig requestConfig) {
        final RepeatableContentHttpRequest<I> repeatableRequest = getRepeatableRequest(request);
        final RetryHandler retryHandler = (errorHandler == null) ? getRequestRetryHandler(request, requestConfig) : errorHandler;
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<HttpClientResponse<ByteBuf>>() {
            @Override
            public Observable<HttpClientResponse<ByteBuf>> getObservableForEndpoint(
                    Server server) {
                return delegate.submit(server.getHost(), server.getPort(), repeatableRequest, requestConfig);
            }
        }, retryHandler);
    }
}
