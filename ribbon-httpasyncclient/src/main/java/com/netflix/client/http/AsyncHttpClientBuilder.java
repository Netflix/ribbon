/*
 *
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.client.http;

import java.nio.ByteBuffer;
import java.util.List;

import rx.Observable;

import com.netflix.client.AsyncClient;
import com.netflix.client.AsyncLoadBalancingClient;
import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.LoadBalancerErrorHandler;
import com.netflix.client.ObservableAsyncClient;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.httpasyncclient.HttpAsyncClientLoadBalancerErrorHandler;
import com.netflix.httpasyncclient.RibbonHttpAsyncClient;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.SerializationFactory;

/**
 * Builder to build an asynchronous HTTP client.
 * 
 * @author awang
 *
 * @param <T> Type of storage used for delivering partial content, for example, {@link ByteBuffer}
 */
public class AsyncHttpClientBuilder<T> {
    
    private AsyncHttpClientBuilder() {}
    
    /**
     * Builder for building an {@link AsyncLoadBalancingClient}
     * 
     * @author awang
     *
     * @param <T> Type of storage used for delivering partial content, for example, {@link ByteBuffer}
     */
    public static class LoadBalancerClientBuilder<T> {
        
        AsyncLoadBalancingHttpClient<T> lbClient;
        
        private LoadBalancerClientBuilder(
                AsyncClient<HttpRequest, HttpResponse, T, ContentTypeBasedSerializerKey> client, ILoadBalancer lb, LoadBalancerErrorHandler<HttpRequest, HttpResponse> defaultErrorHandler) {
            lbClient = new AsyncLoadBalancingHttpClient<T>(client, DefaultClientConfigImpl.getClientConfigWithDefaultValues());
            lbClient.setLoadBalancer(lb);
            if (defaultErrorHandler != null) {
                lbClient.setErrorHandler(defaultErrorHandler);
            }
        }
        
        private LoadBalancerClientBuilder(
                AsyncClient<HttpRequest, HttpResponse, T, ContentTypeBasedSerializerKey> client, IClientConfig clientConfig, LoadBalancerErrorHandler<HttpRequest, HttpResponse> defaultErrorHandler) {
            lbClient = new AsyncLoadBalancingHttpClient<T>(client, clientConfig);
            ILoadBalancer loadBalancer = null;
            try {
                loadBalancer = ClientFactory.registerNamedLoadBalancerFromclientConfig(clientConfig.getClientName(), clientConfig);
            } catch (ClientException e) {
                throw new RuntimeException(e);
            }
            lbClient.setLoadBalancer(loadBalancer);
            if (defaultErrorHandler != null) {
                lbClient.setErrorHandler(defaultErrorHandler);
            }
        }
        
        private LoadBalancerClientBuilder(
                AsyncClient<HttpRequest, HttpResponse, T, ContentTypeBasedSerializerKey> client, List<Server> serverList, LoadBalancerErrorHandler<HttpRequest, HttpResponse> defaultErrorHandler) {
            lbClient = new AsyncLoadBalancingHttpClient<T>(client, DefaultClientConfigImpl.getClientConfigWithDefaultValues());
            BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
            lb.setServersList(serverList);
            lbClient.setLoadBalancer(lb);
            if (defaultErrorHandler != null) {
                lbClient.setErrorHandler(defaultErrorHandler);
            }
        }

        /**
         * Set the errorHandler for the load balancer
         *
         * @see LoadBalancerErrorHandler
         */
        public LoadBalancerClientBuilder<T> withErrorHandler(LoadBalancerErrorHandler<HttpRequest, HttpResponse> errorHandler) {
            lbClient.setErrorHandler(errorHandler);
            return this;
        }
        
        /**
         * Build an {@link AsyncLoadBalancingClient} that is capable of handling both buffered and streaming content
         */
        public AsyncLoadBalancingHttpClient<T> build() {
            return lbClient;
        }
        
        /**
         * Build an {@link AsyncLoadBalancingClient} wrapped by an {@link ObservableAsyncClient}
         */
        public ObservableAsyncClient<HttpRequest, HttpResponse, T> observableClient() {
            return new ObservableAsyncClient<HttpRequest, HttpResponse, T>(lbClient);
        }
    }
    
    private AsyncClient<HttpRequest, HttpResponse, T, ContentTypeBasedSerializerKey> client;
    private IClientConfig config;
    private LoadBalancerErrorHandler<HttpRequest, HttpResponse> defaultErrorHandler;
    
    public static AsyncHttpClientBuilder<ByteBuffer> withApacheAsyncClient() {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
        return withApacheAsyncClient(config);
    }
    
    /**
     * Build a client with the configuration based on {@link ClientFactory#getNamedConfig(String)}
     */
    public static AsyncHttpClientBuilder<ByteBuffer> withApacheAsyncClient(String name) {
        IClientConfig config = ClientFactory.getNamedConfig(name);
        return withApacheAsyncClient(config);
    }

    /**
     * Start a builder based on Apache's HttpAsyncClient
     * 
     * @param clientConfig configuration to be used by the client
     */
    public static AsyncHttpClientBuilder<ByteBuffer> withApacheAsyncClient(IClientConfig clientConfig) {
        AsyncHttpClientBuilder<ByteBuffer> builder = new AsyncHttpClientBuilder<ByteBuffer>();
        builder.client = new RibbonHttpAsyncClient(clientConfig);
        builder.config = clientConfig;
        builder.defaultErrorHandler = new HttpAsyncClientLoadBalancerErrorHandler();
        return builder;
    }

    /**
     * Build an {@link AsyncLoadBalancingClient} with the specified load balancer
     *  
     * @param lb
     */
    public LoadBalancerClientBuilder<T> withLoadBalancer(ILoadBalancer lb) {
        LoadBalancerClientBuilder<T> lbBuilder = new LoadBalancerClientBuilder<T>(this.client, lb, this.defaultErrorHandler);
        return lbBuilder;
    }
    
    /**
     * Build an {@link AsyncLoadBalancingClient} with the load balancer created from the
     * the same client configuration
     */
    public LoadBalancerClientBuilder<T> withLoadBalancer() {
        LoadBalancerClientBuilder<T> lbBuilder = new LoadBalancerClientBuilder<T>(this.client, this.config, this.defaultErrorHandler);
        return lbBuilder;        
    }
    
    /**
     * Create an {@link AsyncLoadBalancingClient} with a {@link BaseLoadBalancer} and a fixed server list
     * @param serverList
     */
    public LoadBalancerClientBuilder<T> balancingWithServerList(List<Server> serverList) {
        LoadBalancerClientBuilder<T> lbBuilder = new LoadBalancerClientBuilder<T>(this.client, serverList, this.defaultErrorHandler);
        return lbBuilder;                
    }
    
    /**
     * add a {@link SerializationFactory}
     * 
     *  @see AsyncClient#addSerializationFactory(SerializationFactory)
     */
    public AsyncHttpClientBuilder<T> withSerializationFactory(
            SerializationFactory<ContentTypeBasedSerializerKey> factory) {
        client.addSerializationFactory(factory);
        return this;
    }
    
    /**
     * Build a client that is capable of handling both buffered and non-buffered (streaming) response
     */
    public AsyncHttpClient<T> buildClient() {
        return (AsyncHttpClient<T>) client;
    }
    
    /**
     * Build a client that is capable of handling buffered response
     */
    public AsyncBufferingHttpClient buildBufferingClient() {
        return (AsyncBufferingHttpClient) client;
    }
    
    /**
     * Build an {@link ObservableAsyncClient} that provides {@link Observable} APIs
     */
    public ObservableAsyncClient<HttpRequest, HttpResponse, T> observableClient() {
        return new ObservableAsyncClient<HttpRequest, HttpResponse, T>(client);
    }
}
