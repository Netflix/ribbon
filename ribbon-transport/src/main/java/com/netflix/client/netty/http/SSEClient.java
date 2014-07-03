package com.netflix.client.netty.http;

import io.netty.channel.ChannelOption;
import io.reactivex.netty.client.PoolStats;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;

import rx.Observable;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.ILoadBalancer;

public class SSEClient<I> extends NettyHttpClient<I, ServerSentEvent> {
    
    public SSEClient(
            ILoadBalancer lb,
            IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipelineConfigurator) {
        super(lb, config, retryHandler, pipelineConfigurator, null);
    }

    public SSEClient(
            IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipelineConfigurator) {
        super(config, retryHandler, pipelineConfigurator, null);
    }

    public SSEClient(
            ILoadBalancer lb,
            PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipeLineConfigurator) {
        super(lb, pipeLineConfigurator, null);
    }

    @Override
    protected HttpClient<I, ServerSentEvent> getRxClient(String host, int port) {
        HttpClientBuilder<I, ServerSentEvent> clientBuilder =
                new HttpClientBuilder<I, ServerSentEvent>(host, port).pipelineConfigurator(pipelineConfigurator);
        int requestConnectTimeout = getProperty(IClientConfigKey.Keys.ConnectTimeout, null, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        RxClient.ClientConfig rxClientConfig = new HttpClientConfig.Builder().build();
        
        HttpClient<I, ServerSentEvent> client = clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, requestConnectTimeout).config(rxClientConfig).build();
        return client;
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
