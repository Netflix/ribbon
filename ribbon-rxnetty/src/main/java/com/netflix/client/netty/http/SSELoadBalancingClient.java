package com.netflix.client.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;
import rx.Observable;

import com.google.common.base.Preconditions;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ClientObservableProvider;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerExecutor;
import com.netflix.loadbalancer.Server;

public class SSELoadBalancingClient<I> extends SSEClient<I> {

    private LoadBalancerExecutor lbExecutor;
    
    public static SSELoadBalancingClient<ByteBuf> createDefaultLoadBalancingSSEClient(ILoadBalancer lb) {
        return createDefaultLoadBalancingSSEClient(lb, 
                DefaultClientConfigImpl.getClientConfigWithDefaultValues(), null);
    }

    public static SSELoadBalancingClient<ByteBuf> createDefaultLoadBalancingSSEClient(ILoadBalancer lb, IClientConfig config) {
        return createDefaultLoadBalancingSSEClient(lb, config, null);
    }

    
    public static SSELoadBalancingClient<ByteBuf> createDefaultLoadBalancingSSEClient(ILoadBalancer lb, IClientConfig config, RetryHandler handler) {
        return new SSELoadBalancingClient<ByteBuf>(lb, config, 
                SSELoadBalancingClient.DEFAULT_PIPELINE_CONFIGURATOR, handler);
    }

    public SSELoadBalancingClient(ILoadBalancer lb, IClientConfig config, 
            PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipelineConfigurator) {
        this(lb, config, pipelineConfigurator, null);
    }
        
    public SSELoadBalancingClient(ILoadBalancer lb, IClientConfig config, 
            PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipelineConfigurator, RetryHandler defaultErrorHandler) {
        super(config, pipelineConfigurator);
        Preconditions.checkNotNull(lb);
        RetryHandler handler = (defaultErrorHandler == null) ? new NettyHttpLoadBalancerErrorHandler(config) : defaultErrorHandler;
        lbExecutor = new LoadBalancerExecutor(lb, config, handler);
    }
    
    public Observable<HttpClientResponse<ServerSentEvent>> submit(final HttpClientRequest<I> request) {
        return submit(request, null);
    }
    
    private RequestSpecificRetryHandler getRequestRetryHandler(HttpClientRequest<?> request, IClientConfig requestConfig) {
        boolean okToRetryOnAllErrors = request.getMethod().equals(HttpMethod.GET);
        return new RequestSpecificRetryHandler(true, okToRetryOnAllErrors, lbExecutor.getErrorHandler(), requestConfig);
    }

    public Observable<HttpClientResponse<ServerSentEvent>> submit(final HttpClientRequest<I> request, final IClientConfig requestConfig) {
        final RepeatableContentHttpRequest<I> repeatbleRequest = getRepeatableRequest(request);
        return lbExecutor.executeWithLoadBalancer(new ClientObservableProvider<HttpClientResponse<ServerSentEvent>>() {
            @Override
            public Observable<HttpClientResponse<ServerSentEvent>> getObservableForEndpoint(
                    Server server) {
                return submit(server.getHost(), server.getPort(), repeatbleRequest, requestConfig);
            }
        }, getRequestRetryHandler(request, requestConfig));
    }
    
    public Observable<ServerSentEvent> observeServerSentEvent(final HttpClientRequest<I> request, final IClientConfig requestConfig) {
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
