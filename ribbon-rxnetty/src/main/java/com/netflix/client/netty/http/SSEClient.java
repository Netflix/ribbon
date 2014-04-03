package com.netflix.client.netty.http;

import rx.Observable;
import rx.functions.Func1;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.HttpClient.HttpClientConfig;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;

import com.google.common.base.Preconditions;
import com.netflix.client.ClientObservableProvider;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.Server;

public class SSEClient extends AbstractNettyHttpClient<ServerSentEvent> {
    
    public SSEClient() {
        super();
    }

    public SSEClient(IClientConfig config) {
        super(config);
    }

    @Override
    protected <I> HttpClient<I, ServerSentEvent> getRxClient(String host,
            int port, IClientConfig requestConfig) {
        HttpClientBuilder<I, ServerSentEvent> clientBuilder =
                new HttpClientBuilder<I, ServerSentEvent>(host, port).pipelineConfigurator(PipelineConfigurators.<I>sseClientConfigurator());
        int requestConnectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, requestConfig);
        HttpClientConfig.Builder builder = new HttpClientConfig.Builder(HttpClientConfig.DEFAULT_CONFIG).followRedirect();
        RxClient.ClientConfig rxClientConfig = builder.build();
        
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
