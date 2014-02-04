package com.netflix.client.netty.http;

import java.util.List;

import io.netty.bootstrap.Bootstrap;

import com.google.common.base.Preconditions;
import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.LoadBalancerErrorHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.SerializationFactory;

public class NettyHttpClientBuilder {

    private NettyHttpClientBuilder() {}

    private IClientConfig clientConfig = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
    private Bootstrap bootStrap;
    private SerializationFactory<HttpSerializationContext> serializationFactory;

    public static class NettyHttpLoadBalancingClientBuilder extends NettyHttpClientBuilder {
        private final NettyHttpClientBuilder clientBuilder;
        private ILoadBalancer lb;
        private List<Server> serverList;
        private LoadBalancerErrorHandler<HttpRequest, HttpResponse> errorHandler = new NettyHttpLoadBalancerErrorHandler();

        private NettyHttpLoadBalancingClientBuilder(NettyHttpClientBuilder clientBuilder, ILoadBalancer lb) {
            this.clientBuilder = clientBuilder;
            this.lb = lb;
        }

        private NettyHttpLoadBalancingClientBuilder(NettyHttpClientBuilder clientBuilder, List<Server> serverList) {
            this.clientBuilder = clientBuilder;
            this.serverList = serverList;
        }

        public NettyHttpLoadBalancingClientBuilder withLoadBalancerErrorHandler(LoadBalancerErrorHandler<HttpRequest, HttpResponse> errorHandler) {
            Preconditions.checkNotNull(errorHandler);
            this.errorHandler = errorHandler;
            return this;
        }

        @Override
        public NettyHttpLoadBalancingClientBuilder withLoadBalancer(AbstractLoadBalancer lb) {
            Preconditions.checkNotNull(lb);
            this.lb = lb;
            return this;
        }

        @Override
        public NettyHttpLoadBalancingClientBuilder withClientConfig(IClientConfig clientConfig) {
            clientBuilder.withClientConfig(clientConfig);
            return this;
        }

        @Override
        public NettyHttpClientBuilder withSerializationFactory(
                SerializationFactory<HttpSerializationContext> serializationFactory) {
            clientBuilder.withSerializationFactory(serializationFactory);
            return this;
        }

        @Override
        public NettyHttpClientBuilder withBootStrap(Bootstrap bootStrap) {
            clientBuilder.withBootStrap(bootStrap);
            return this;
        }

        @Override
        public NettyHttpLoadBalancingClientBuilder withInitialServerList(
                List<Server> serverList) {
            Preconditions.checkNotNull(serverList);
            this.serverList = serverList;
            return this;
        }
        
        @Override
        public NettyHttpLoadBalancingClientBuilder enableLoadBalancing() {
            return this;
        }

        @Override
        public NettyHttpLoadBalancingClient build() {
            Preconditions.checkNotNull(clientBuilder.clientConfig);
            Preconditions.checkNotNull(errorHandler);
            if (lb == null) {
                try {
                    lb = ClientFactory.registerNamedLoadBalancerFromclientConfig(clientBuilder.clientConfig.getClientName(), 
                            clientBuilder.clientConfig);
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                }                   
            }
            if (serverList != null && (lb instanceof BaseLoadBalancer)) {
                ((BaseLoadBalancer) lb).setServersList(serverList);
            }
            NettyHttpLoadBalancingClient client = new NettyHttpLoadBalancingClient(clientBuilder.clientConfig, 
                    errorHandler, clientBuilder.serializationFactory, clientBuilder.bootStrap);
            client.setLoadBalancer(lb);
            return client;
        }
    }

    public static NettyHttpClientBuilder newBuilder() {
        return new NettyHttpClientBuilder();
    }

    public NettyHttpLoadBalancingClientBuilder withLoadBalancer(AbstractLoadBalancer lb) {
        Preconditions.checkNotNull(lb);
        return new NettyHttpLoadBalancingClientBuilder(this, lb);
    }

    public NettyHttpClientBuilder withClientConfig(IClientConfig clientConfig) {
        Preconditions.checkNotNull(clientConfig);
        this.clientConfig = clientConfig;
        return this;
    }

    public NettyHttpClientBuilder withSerializationFactory(SerializationFactory<HttpSerializationContext> serializationFactory) {
        Preconditions.checkNotNull(serializationFactory);
        this.serializationFactory = serializationFactory;
        return this;
    }

    public NettyHttpClientBuilder withBootStrap(Bootstrap bootStrap) {
        Preconditions.checkNotNull(bootStrap);
        this.bootStrap = bootStrap;
        return this;
    }

    public NettyHttpLoadBalancingClientBuilder withInitialServerList(List<Server> serverList) {   
        Preconditions.checkNotNull(serverList);
        return new NettyHttpLoadBalancingClientBuilder(this, serverList);
    }

    public NettyHttpLoadBalancingClientBuilder enableLoadBalancing() {
        return new NettyHttpLoadBalancingClientBuilder(this, (ILoadBalancer) null);
    }
    
    public NettyHttpClient build() {
        Preconditions.checkNotNull(clientConfig);
        if (clientConfig.getPropertyWithType(CommonClientConfigKey.InitializeNFLoadBalancer, false)) {
            try {
                ILoadBalancer lb = ClientFactory.registerNamedLoadBalancerFromclientConfig(clientConfig.getClientName(), clientConfig);
                NettyHttpLoadBalancingClientBuilder lbBuilder = new NettyHttpLoadBalancingClientBuilder(this, lb);
                return lbBuilder.build();
            } catch (ClientException e) {
                throw new RuntimeException(e);
            }
        }
        return new NettyHttpClient(this.clientConfig, this.serializationFactory, this.bootStrap);
    }
}
