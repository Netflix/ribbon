package com.netflix.client.netty.http;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.client.PoolStats;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClient.HttpClientConfig;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;
import rx.Observable;
import rx.functions.Func1;

import com.google.common.base.Preconditions;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.ClientObservableProvider;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerExecutor;
import com.netflix.loadbalancer.Server;

class SSEClient<I> extends NettyHttpClient<I, ServerSentEvent> {
    
    protected static final PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<ByteBuf>> DEFAULT_PIPELINE_CONFIGURATOR = 
            PipelineConfigurators.sseClientConfigurator();

    public SSEClient(ILoadBalancer lb, IClientConfig config, 
            PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipelineConfigurator) {
        this(lb, config, pipelineConfigurator, null);
    }
        
    public SSEClient(ILoadBalancer lb, IClientConfig config, 
            PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipelineConfigurator, RetryHandler defaultErrorHandler) {
        super(lb, config, pipelineConfigurator, defaultErrorHandler);
    }
        
    @Override
    protected HttpClient<I, ServerSentEvent> getRxClient(String host, int port) {
        HttpClientBuilder<I, ServerSentEvent> clientBuilder =
                new HttpClientBuilder<I, ServerSentEvent>(host, port).pipelineConfigurator(pipeLineConfigurator);
        int requestConnectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, null, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        RxClient.ClientConfig rxClientConfig = new HttpClientConfig.Builder().build();
        
        HttpClient<I, ServerSentEvent> client = clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, requestConnectTimeout).config(rxClientConfig).build();
        return client;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public PoolStats getStats() {
        return null;
    }

    @Override
    public void shutdown() {
    }

    @Override
    public Observable<PoolStateChangeEvent> poolStateChangeObservable() {
        return Observable.empty();
    }
}
