package com.netflix.client.netty.http;

import io.netty.bootstrap.Bootstrap;
import io.reactivex.netty.protocol.http.ObservableHttpResponse;
import io.reactivex.netty.protocol.text.sse.SSEEvent;
import rx.Observable;

import com.netflix.client.LoadBalancerErrorHandler;
import com.netflix.client.ClientObservableProvider;
import com.netflix.client.LoadBalancerObservables;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.TypeDef;

public class NettyHttpLoadBalancingClient extends NettyHttpClient {

    private LoadBalancerObservables<HttpRequest, HttpResponse> lbObservables;
    private final NettyHttpClient delegate;
    
    public NettyHttpLoadBalancingClient() {
        this(DefaultClientConfigImpl.getClientConfigWithDefaultValues());
    }
    
    public NettyHttpLoadBalancingClient(IClientConfig config) {
        delegate = new NettyHttpClient(config);
        lbObservables = new LoadBalancerObservables<HttpRequest, HttpResponse>(config);
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
    }
    
    public NettyHttpLoadBalancingClient(IClientConfig config, LoadBalancerErrorHandler<HttpRequest, HttpResponse> errorHandler, 
            SerializationFactory<HttpSerializationContext> serializationFactory, Bootstrap bootStrap) {
        delegate = new NettyHttpClient(config, serializationFactory, bootStrap);
        this.lbObservables = new LoadBalancerObservables<HttpRequest, HttpResponse>(config);
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

    @Override
    public <T> Observable<ServerSentEvent<T>> createServerSentEventEntityObservable(
            final HttpRequest request, final TypeDef<T> typeDef) {
        return lbObservables.retryWithLoadBalancer(request, new ClientObservableProvider<ServerSentEvent<T>, HttpRequest>() {

            @Override
            public Observable<ServerSentEvent<T>> getObservableForEndpoint(
                    HttpRequest request) {
                return delegate.createServerSentEventEntityObservable(request, typeDef);
            }
            
        });
    }

    @Override
    public Observable<ObservableHttpResponse<SSEEvent>> createServerSentEventObservable(
            HttpRequest request) {
        return lbObservables.retryWithLoadBalancer(request, new ClientObservableProvider<ObservableHttpResponse<SSEEvent>, HttpRequest>() {

            @Override
            public Observable<ObservableHttpResponse<SSEEvent>> getObservableForEndpoint(HttpRequest _request) {
                return delegate.createServerSentEventObservable(_request);
            }
        });
    }

    @Override
    public Observable<HttpResponse> createFullHttpResponseObservable(
            HttpRequest request) {
        return lbObservables.retryWithLoadBalancer(request, new ClientObservableProvider<HttpResponse, HttpRequest>() {

            @Override
            public Observable<HttpResponse> getObservableForEndpoint(
                    HttpRequest request) {
                return delegate.createFullHttpResponseObservable(request);
            }
            
        });
    }

    @Override
    public <T> Observable<T> createEntityObservable(HttpRequest request,
            final TypeDef<T> typeDef) {
        return lbObservables.retryWithLoadBalancer(request, new ClientObservableProvider<T, HttpRequest>() {

            @Override
            public Observable<T> getObservableForEndpoint(HttpRequest _request) {
                return delegate.createEntityObservable(_request, typeDef);
            }
        });
   }

    public void setLoadBalancer(ILoadBalancer lb) {
        lbObservables.setLoadBalancer(lb);
    }
    
    public ILoadBalancer getLoadBalancer() {
        return lbObservables.getLoadBalancer();
    }
    
    public int getMaxAutoRetriesNextServer() {
        return lbObservables.getMaxAutoRetriesNextServer();
    }

    public void setMaxAutoRetriesNextServer(int maxAutoRetriesNextServer) {
        lbObservables.setMaxAutoRetriesNextServer(maxAutoRetriesNextServer);
    }

    public int getMaxAutoRetries() {
        return lbObservables.getMaxAutoRetries();
    }

    public void setMaxAutoRetries(int maxAutoRetries) {
        lbObservables.setMaxAutoRetries(maxAutoRetries);
    }

    public ServerStats getServerStats(Server server) {
        return lbObservables.getServerStats(server);
    }

}
