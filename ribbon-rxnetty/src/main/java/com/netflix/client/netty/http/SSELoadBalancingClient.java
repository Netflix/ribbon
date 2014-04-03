package com.netflix.client.netty.http;

import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;
import rx.Observable;

import com.netflix.client.ClientObservableProvider;
import com.netflix.client.LoadBalancerExecutor;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

public class SSELoadBalancingClient extends AbstractLoadBalancingClient {

    private final SSEClient delegate;
    
    public SSELoadBalancingClient(ILoadBalancer lb, IClientConfig config) {
        delegate = new SSEClient(config);
        lbExecutor = new LoadBalancerExecutor(lb, config);
        lbExecutor.setErrorHandler(new NettyHttpLoadBalancerErrorHandler(config));
    }
        
    public SSELoadBalancingClient(ILoadBalancer lb, IClientConfig config, RetryHandler errorHandler) {
        delegate = new SSEClient(config);
        lbExecutor = new LoadBalancerExecutor(lb, config);
        lbExecutor.setErrorHandler(errorHandler);
    }
    
    public <I> Observable<HttpClientResponse<ServerSentEvent>> submit(final HttpClientRequest<I> request) {
        return submit(request, null);
    }
    
    public <I> Observable<HttpClientResponse<ServerSentEvent>> submit(final HttpClientRequest<I> request, final IClientConfig requestConfig) {
        final RepeatableContentHttpRequest<I> repeatbleRequest = getRepeatableRequest(request);
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<HttpClientResponse<ServerSentEvent>>() {
            @Override
            public Observable<HttpClientResponse<ServerSentEvent>> getObservableForEndpoint(
                    Server server) {
                return delegate.submit(server.getHost(), server.getPort(), repeatbleRequest, requestConfig);
            }
        }, getRequestRetryHandler(request, requestConfig));
    }
    
    public <I> Observable<ServerSentEvent> observeServerSentEvent(final HttpClientRequest<I> request, final IClientConfig requestConfig) {
        final RepeatableContentHttpRequest<I> repeatbleRequest = getRepeatableRequest(request);
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<ServerSentEvent>() {
            @Override
            public Observable<ServerSentEvent> getObservableForEndpoint(
                    Server server) {
                return delegate.observeServerSentEvent(server.getHost(), server.getPort(), repeatbleRequest, requestConfig);
            }
        }, getRequestRetryHandler(request, requestConfig));
    }
}
