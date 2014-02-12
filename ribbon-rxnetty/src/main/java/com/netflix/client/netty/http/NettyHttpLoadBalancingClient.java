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

import java.net.URI;

import javax.annotation.Nullable;

import io.netty.bootstrap.Bootstrap;
import io.reactivex.netty.protocol.http.ObservableHttpResponse;
import io.reactivex.netty.protocol.text.sse.SSEEvent;
import rx.Observable;

import com.netflix.client.RetryHandler;
import com.netflix.client.ClientObservableProvider;
import com.netflix.client.LoadBalancerExecutor;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequestRetryHandler;
import com.netflix.client.http.HttpResponse;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.TypeDef;

public class NettyHttpLoadBalancingClient extends NettyHttpClient {

    private final LoadBalancerExecutor lbObservables;
    private final NettyHttpClient delegate;

    public NettyHttpLoadBalancingClient() {
        this(null, DefaultClientConfigImpl.getClientConfigWithDefaultValues());
    }
    
    public NettyHttpLoadBalancingClient(ILoadBalancer lb, IClientConfig config) {
        delegate = new NettyHttpClient(config);
        lbObservables = new LoadBalancerExecutor(lb, config);
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler(config));
    }
    
    public NettyHttpLoadBalancingClient(ILoadBalancer lb, IClientConfig config, RetryHandler errorHandler) {
        delegate = new NettyHttpClient(config);
        lbObservables = new LoadBalancerExecutor(lb, config);
        lbObservables.setErrorHandler(errorHandler);
    }
    
    public NettyHttpLoadBalancingClient(ILoadBalancer lb, IClientConfig config, RetryHandler errorHandler, 
            SerializationFactory<HttpSerializationContext> serializationFactory, Bootstrap bootStrap) {
        delegate = new NettyHttpClient(config, serializationFactory, bootStrap);
        this.lbObservables = new LoadBalancerExecutor(lb, config);
        lbObservables.setErrorHandler(errorHandler);
    }
    
    @Override
    public IClientConfig getConfig() {
        return delegate.getConfig();
    }

    @Override
    public SerializationFactory<HttpSerializationContext> getSerializationFactory() {
        return delegate.getSerializationFactory();
    }

    HttpRequest createRequest(Server server, HttpRequest original) {
        URI uri = lbObservables.reconstructURIWithServer(server, original.getUri());
        return original.replaceUri(uri);
    }
    
    
    @Override
    public <T> Observable<ServerSentEvent<T>> createServerSentEventEntityObservable(
            final HttpRequest request, final TypeDef<T> typeDef, final IClientConfig requestConfig) {
        return lbObservables.retryWithLoadBalancer(request.getUri(), new ClientObservableProvider<ServerSentEvent<T>>() {

            @Override
            public Observable<ServerSentEvent<T>> getObservableForEndpoint(Server server) {
                return delegate.createServerSentEventEntityObservable(createRequest(server, request), typeDef, requestConfig);
            }
            
        }, new HttpRequestRetryHandler(request, requestConfig, lbObservables.getErrorHandler()), request.getLoadBalancerKey());
    }

    @Override
    public Observable<ObservableHttpResponse<SSEEvent>> createServerSentEventObservable(
            final HttpRequest request, final IClientConfig requestConfig) {
        return lbObservables.retryWithLoadBalancer(request.getUri(), new ClientObservableProvider<ObservableHttpResponse<SSEEvent>>() {

            @Override
            public Observable<ObservableHttpResponse<SSEEvent>> getObservableForEndpoint(Server server) {
                return delegate.createServerSentEventObservable(createRequest(server, request), requestConfig);
            }
            
        }, new HttpRequestRetryHandler(request, requestConfig, lbObservables.getErrorHandler()), request.getLoadBalancerKey());
    }

    @Override
    public Observable<HttpResponse> createFullHttpResponseObservable(
            final HttpRequest request, final IClientConfig requestConfig) {
        return lbObservables.retryWithLoadBalancer(request.getUri(), new ClientObservableProvider<HttpResponse>() {

            @Override
            public Observable<HttpResponse> getObservableForEndpoint(
                    Server server) {
                return delegate.createFullHttpResponseObservable(createRequest(server, request), requestConfig);
            }
            
        }, new HttpRequestRetryHandler(request, requestConfig, lbObservables.getErrorHandler()), request.getLoadBalancerKey());
    }

    public <T> Observable<T> createEntityObservable(final HttpRequest request,
            final TypeDef<T> typeDef, final IClientConfig requestConfig, @Nullable final RetryHandler retryHandler) {
        final RetryHandler handler = retryHandler == null ? 
                new HttpRequestRetryHandler(request, requestConfig, lbObservables.getErrorHandler()) : retryHandler;
        return lbObservables.retryWithLoadBalancer(request.getUri(), new ClientObservableProvider<T>() {

            @Override
            public Observable<T> getObservableForEndpoint(Server server) {
                return delegate.createEntityObservable(createRequest(server, request), typeDef, requestConfig);
            }
        }, handler, request.getLoadBalancerKey());
   }

    
    @Override
    public <T> Observable<T> createEntityObservable(final HttpRequest request,
            final TypeDef<T> typeDef, final IClientConfig requestConfig) {
        return createEntityObservable(request, typeDef, requestConfig, null);
   }

    public void setLoadBalancer(ILoadBalancer lb) {
        lbObservables.setLoadBalancer(lb);
    }
    
    public ILoadBalancer getLoadBalancer() {
        return lbObservables.getLoadBalancer();
    }
    
    public int getMaxAutoRetriesNextServer() {
        if (lbObservables.getErrorHandler() != null) {
            return lbObservables.getErrorHandler().getMaxRetriesOnNextServer();
        }
        return 0;
    }

    public int getMaxAutoRetries() {
        if (lbObservables.getErrorHandler() != null) {
            return lbObservables.getErrorHandler().getMaxRetriesOnSameServer();
        }
        return 0;
    }

    public ServerStats getServerStats(Server server) {
        return lbObservables.getServerStats(server);
    }
    
    

}
