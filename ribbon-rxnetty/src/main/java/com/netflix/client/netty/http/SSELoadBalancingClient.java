package com.netflix.client.netty.http;

import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;
import rx.Observable;

import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ClientObservableProvider;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerExecutor;
import com.netflix.loadbalancer.Server;

public class SSELoadBalancingClient extends SSEClient {

    private LoadBalancerExecutor lbExecutor;
    
    public SSELoadBalancingClient(ILoadBalancer lb, IClientConfig config) {
        this(lb, config, new NettyHttpLoadBalancerErrorHandler(config));
    }
        
    public SSELoadBalancingClient(ILoadBalancer lb, IClientConfig config, RetryHandler defaultErrorHandler) {
        lbExecutor = new LoadBalancerExecutor(lb, config);
        lbExecutor.setErrorHandler(defaultErrorHandler);
    }
    
    public <I> Observable<HttpClientResponse<ServerSentEvent>> submit(final HttpClientRequest<I> request) {
        return submit(request, null);
    }
    
    private RequestSpecificRetryHandler getRequestRetryHandler(HttpClientRequest<?> request, IClientConfig requestConfig) {
        boolean okToRetryOnAllErrors = request.getMethod().equals(HttpMethod.GET);
        return new RequestSpecificRetryHandler(true, okToRetryOnAllErrors, lbExecutor.getErrorHandler(), requestConfig);
    }

    public <I> Observable<HttpClientResponse<ServerSentEvent>> submit(final HttpClientRequest<I> request, final IClientConfig requestConfig) {
        final RepeatableContentHttpRequest<I> repeatbleRequest = getRepeatableRequest(request);
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<HttpClientResponse<ServerSentEvent>>() {
            @Override
            public Observable<HttpClientResponse<ServerSentEvent>> getObservableForEndpoint(
                    Server server) {
                return submit(server.getHost(), server.getPort(), repeatbleRequest, requestConfig);
            }
        }, getRequestRetryHandler(request, requestConfig));
    }
    
    public <I> Observable<ServerSentEvent> observeServerSentEvent(final HttpClientRequest<I> request, final IClientConfig requestConfig) {
        final RepeatableContentHttpRequest<I> repeatbleRequest = getRepeatableRequest(request);
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<ServerSentEvent>() {
            @Override
            public Observable<ServerSentEvent> getObservableForEndpoint(
                    Server server) {
                return observeServerSentEvent(server.getHost(), server.getPort(), repeatbleRequest, requestConfig);
            }
        }, getRequestRetryHandler(request, requestConfig));
    }
}
