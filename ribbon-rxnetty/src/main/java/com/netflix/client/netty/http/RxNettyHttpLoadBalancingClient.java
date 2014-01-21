package com.netflix.client.netty.http;

import io.netty.bootstrap.Bootstrap;
import rx.Observable;
import rx.netty.protocol.http.HttpProtocolHandler;
import rx.netty.protocol.http.ObservableHttpResponse;

import com.netflix.client.LoadBalancerErrorHandler;
import com.netflix.client.LoadBalancerObservableRequest;
import com.netflix.client.LoadBalancerObservables;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.JacksonSerializationFactory;
import com.netflix.serialization.SerializationFactory;

public class RxNettyHttpLoadBalancingClient extends RxNettyHttpClient {

    private LoadBalancerObservables<HttpRequest, HttpResponse> lbObservables;
    
    public RxNettyHttpLoadBalancingClient() {
        this(DefaultClientConfigImpl.getClientConfigWithDefaultValues());
    }
    
    public RxNettyHttpLoadBalancingClient(IClientConfig config) {
        super(config);
        lbObservables = new LoadBalancerObservables<HttpRequest, HttpResponse>(config);
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
    }
    
    public RxNettyHttpLoadBalancingClient(IClientConfig config, LoadBalancerErrorHandler<HttpRequest, HttpResponse> errorHandler, 
            SerializationFactory<HttpSerializationContext> serializationFactory, Bootstrap bootStrap) {
        super(config, serializationFactory, bootStrap);
        this.lbObservables = new LoadBalancerObservables<HttpRequest, HttpResponse>(config);
        lbObservables.setErrorHandler(errorHandler);
    }
    
    private <T> Observable<ObservableHttpResponse<T>> executeSuper(final HttpRequest request, HttpProtocolHandler<T> protocolHandler) {
        return super.createObservableHttpResponse(request, protocolHandler);
    }
    
    @Override
    public <T> Observable<ObservableHttpResponse<T>> createObservableHttpResponse(final HttpRequest request, final HttpProtocolHandler<T> protocolHandler) {
        return lbObservables.retryWithLoadBalancer(request, new LoadBalancerObservableRequest<ObservableHttpResponse<T>, HttpRequest>() {
            @Override
            public Observable<ObservableHttpResponse<T>> getSingleServerObservable(
                    HttpRequest request) {
                return executeSuper(request, protocolHandler);
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
