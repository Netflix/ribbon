package com.netflix.client.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClient.HttpClientConfig;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;
import rx.Observable;
import rx.functions.Func1;

import com.google.common.base.Preconditions;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

public class SSEClient<I> extends AbstractNettyHttpClient<I, ServerSentEvent> {
    
    protected static final PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<ByteBuf>> DEFAULT_PIPELINE_CONFIGURATOR = 
            PipelineConfigurators.sseClientConfigurator();

    protected final PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipeLineConfigurator;

    public static SSEClient<ByteBuf> getDefaultSSEClient() {
        return getDefaultSSEClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues());
    }
    
    public static SSEClient<ByteBuf> getDefaultSSEClient(IClientConfig config) {
        return new SSEClient<ByteBuf>(config, DEFAULT_PIPELINE_CONFIGURATOR);
    }
    
    public SSEClient(PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipeLineConfigurator) {
        this(DefaultClientConfigImpl.getClientConfigWithDefaultValues(), pipeLineConfigurator);
    }
        
    public SSEClient(IClientConfig config, PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<I>> pipeLineConfigurator) {
        super(config);
        Preconditions.checkNotNull(pipeLineConfigurator);
        this.pipeLineConfigurator = pipeLineConfigurator;
    }

    @Override
    protected HttpClient<I, ServerSentEvent> getRxClient(String host, int port) {
        HttpClientBuilder<I, ServerSentEvent> clientBuilder =
                new HttpClientBuilder<I, ServerSentEvent>(host, port).pipelineConfigurator(pipeLineConfigurator);
        int requestConnectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, null);
        RxClient.ClientConfig rxClientConfig = new HttpClientConfig.Builder().build();
        
        HttpClient<I, ServerSentEvent> client = clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, requestConnectTimeout).config(rxClientConfig).build();
        return client;
    }
    
    public Observable<ServerSentEvent> observeServerSentEvent(String host, int port, final HttpClientRequest<I> request, IClientConfig requestConfig) {
        return submit(host, port, request, requestConfig)
                .flatMap(new Func1<HttpClientResponse<ServerSentEvent>, Observable<ServerSentEvent>>() {
                    @Override
                    public Observable<ServerSentEvent> call(HttpClientResponse<ServerSentEvent> t1) {
                        return t1.getContent();
                    }
                });
    }
    
}
