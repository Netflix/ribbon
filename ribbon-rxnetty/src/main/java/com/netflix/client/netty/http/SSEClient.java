package com.netflix.client.netty.http;

import io.netty.channel.ChannelOption;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClient.HttpClientConfig;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;
import rx.Observable;
import rx.functions.Func1;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

public class SSEClient extends AbstractNettyHttpClient<ServerSentEvent> {
    
    public SSEClient() {
        super();
    }

    public SSEClient(IClientConfig config) {
        super(config);
    }

    @Override
    protected <I> HttpClient<I, ServerSentEvent> getRxClient(String host, int port) {
        HttpClientBuilder<I, ServerSentEvent> clientBuilder =
                new HttpClientBuilder<I, ServerSentEvent>(host, port).pipelineConfigurator(PipelineConfigurators.<I>sseClientConfigurator());
        int requestConnectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, null);
        RxClient.ClientConfig rxClientConfig = new HttpClientConfig.Builder().build();
        
        HttpClient<I, ServerSentEvent> client = clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, requestConnectTimeout).config(rxClientConfig).build();
        return client;
    }
    
    public <I> Observable<ServerSentEvent> observeServerSentEvent(String host, int port, final HttpClientRequest<I> request, IClientConfig requestConfig) {
        return submit(host, port, request, requestConfig)
                .flatMap(new Func1<HttpClientResponse<ServerSentEvent>, Observable<ServerSentEvent>>() {
                    @Override
                    public Observable<ServerSentEvent> call(HttpClientResponse<ServerSentEvent> t1) {
                        return t1.getContent();
                    }
                });
    }
    
}
