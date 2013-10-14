package com.netflix.client.http;

import java.nio.ByteBuffer;
import java.util.List;

import com.google.common.collect.Lists;
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

public class AsyncHttpClientBuilder<T> {
    
    private AsyncHttpClientBuilder() {}
    
    public static class LoadBalancerClientBuilder<T> {
        
        AsyncLoadBalancingHttpClient<T> lbClient;
        IClientConfig clientConfig;
        
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
            this.clientConfig = clientConfig;
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

        public LoadBalancerClientBuilder<T> withErrorHandler(LoadBalancerErrorHandler<HttpRequest, HttpResponse> errorHandler) {
            lbClient.setErrorHandler(errorHandler);
            return this;
        }
        
        public AsyncLoadBalancingHttpClient<T> build() {
            return lbClient;
        }
        
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
    
    public static AsyncHttpClientBuilder<ByteBuffer> withApacheAsyncClient(String name) {
        IClientConfig config = ClientFactory.getNamedConfig(name);
        return withApacheAsyncClient(config);
    }

    
    public static AsyncHttpClientBuilder<ByteBuffer> withApacheAsyncClient(IClientConfig clientConfig) {
        AsyncHttpClientBuilder<ByteBuffer> builder = new AsyncHttpClientBuilder<ByteBuffer>();
        builder.client = new RibbonHttpAsyncClient(clientConfig);
        builder.config = clientConfig;
        builder.defaultErrorHandler = new HttpAsyncClientLoadBalancerErrorHandler();
        return builder;
    }

    public LoadBalancerClientBuilder<T> withLoadBalancer(ILoadBalancer lb) {
        LoadBalancerClientBuilder<T> lbBuilder = new LoadBalancerClientBuilder<T>(this.client, lb, this.defaultErrorHandler);
        return lbBuilder;
    }
    
    public LoadBalancerClientBuilder<T> withLoadBalancer() {
        LoadBalancerClientBuilder<T> lbBuilder = new LoadBalancerClientBuilder<T>(this.client, this.config, this.defaultErrorHandler);
        return lbBuilder;        
    }
    
    public LoadBalancerClientBuilder<T> balancingWithServerList(List<Server> serverList) {
        LoadBalancerClientBuilder<T> lbBuilder = new LoadBalancerClientBuilder<T>(this.client, serverList, this.defaultErrorHandler);
        return lbBuilder;                
    }
    
    public AsyncHttpClientBuilder<T> withSerializationFactory(
            SerializationFactory<ContentTypeBasedSerializerKey> factory) {
        client.setSerializationFactory(factory);
        return this;
    }
    
    public AsyncHttpClient<T> buildClient() {
        return (AsyncHttpClient<T>) client;
    }
    
    public AsyncBufferingHttpClient buildBufferingClient() {
        return (AsyncBufferingHttpClient) client;
    }
    
    public ObservableAsyncClient<HttpRequest, HttpResponse, T> observableClient() {
        return new ObservableAsyncClient<HttpRequest, HttpResponse, T>(client);
    }
}
