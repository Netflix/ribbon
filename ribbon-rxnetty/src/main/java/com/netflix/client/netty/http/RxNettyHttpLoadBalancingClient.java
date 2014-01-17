package com.netflix.client.netty.http;

import rx.Observable;
import rx.netty.protocol.http.HttpProtocolHandler;
import rx.netty.protocol.http.ObservableHttpResponse;

import com.netflix.client.LoadBalancerErrorHandler;
import com.netflix.client.LoadBalancerObservableRequest;
import com.netflix.client.LoadBalancerObservables;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;

public class RxNettyHttpLoadBalancingClient extends RxNettyHttpClient {

    private LoadBalancerObservables<HttpRequest, HttpResponse> lbObservables;
    
    public RxNettyHttpLoadBalancingClient(IClientConfig config, LoadBalancerErrorHandler<HttpRequest, HttpResponse> errorHandler) {
        super(config);
        this.lbObservables = new LoadBalancerObservables<HttpRequest, HttpResponse>(config);
        lbObservables.setErrorHandler(errorHandler);
    }
    
    public RxNettyHttpLoadBalancingClient(RxNettyHttpClient client) {
        this.lbObservables = new LoadBalancerObservables<HttpRequest, HttpResponse>(client.getConfig());
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
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
