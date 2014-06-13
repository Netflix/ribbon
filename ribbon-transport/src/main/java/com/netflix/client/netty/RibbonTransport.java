package com.netflix.client.netty;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;
import io.reactivex.netty.protocol.udp.client.UdpClient;

public final class RibbonTransport {
    
    protected static final PipelineConfigurator<HttpClientResponse<ServerSentEvent>, HttpClientRequest<ByteBuf>> DEFAULT_HTTP_PIPELINE_CONFIGURATOR = 
            PipelineConfigurators.sseClientConfigurator();

    protected static final PipelineConfigurator<HttpClientResponse<ByteBuf>, HttpClientRequest<ByteBuf>> DEFAULT_SSE_PIPELINE_CONFIGURATOR = 
            PipelineConfigurators.httpClientConfigurator();

    private RibbonTransport() {
    }
 
    public static RxClient<ByteBuf, ByteBuf> newTcpClient(ILoadBalancer loadBalancer, IClientConfig config) {
        return null;
    }
    
    public static <I, O> RxClient<I, O> newTcpClient(ILoadBalancer loadBalancer, PipelineConfigurator<O, I> pipelineConfigurator, 
            IClientConfig config) {
        return null;
    }
    
    public static <I, O> RxClient<I, O> newTcpClient(PipelineConfigurator<O, I> pipelineConfigurator, 
            IClientConfig config) {
        return null;
    }

    public static RxClient<ByteBuf, ByteBuf> newTcpClient(IClientConfig config) {
        return null;
    }
 
    public static UdpClient<ByteBuf, ByteBuf> newUdpClient(ILoadBalancer loadBalancer, IClientConfig config) {
        return null;
    }
 
    public static UdpClient<ByteBuf, ByteBuf> newUdpClient(IClientConfig config) {
        return null;
    }
    
    public static <I, O> UdpClient<I, O> newUdpClient(ILoadBalancer loadBalancer, PipelineConfigurator<O, I> pipelineConfigurator, IClientConfig config) {
        return null;
    }
    
    public static <I, O> UdpClient<I, O> newUdpClient(PipelineConfigurator<O, I> pipelineConfigurator, IClientConfig config) {
        return null;
    }


    public static HttpClient<ByteBuf, ByteBuf> newHttpClient(ILoadBalancer loadBalancer, IClientConfig config) {
        return null;
    }
 
    public static HttpClient<ByteBuf, ByteBuf> newHttpClient(IClientConfig config) {
        return null;
    }
    
    public static <I, O> HttpClient<I, O> newHttpClient(PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator, 
            ILoadBalancer loadBalancer, IClientConfig config) {
        return null;
    }
    
    public static <I, O> HttpClient<I, O> newHttpClient(PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator, 
            IClientConfig config) {
        return null;
    }
    
    public static HttpClient<ByteBuf, ByteBuf> newSSEClient(ILoadBalancer loadBalancer, IClientConfig config) {
        return null;
    }
 
    public static HttpClient<ByteBuf, ByteBuf> newSSEClient(IClientConfig config) {
        return null;
    }
    
    public static <I, O> HttpClient<I, O> newSSEClient(PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator, 
            ILoadBalancer loadBalancer, IClientConfig config) {
        return null;
    }
    
    public static <I, O> HttpClient<I, O> newSSEClient(PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator, 
            IClientConfig config) {
        return null;
    }

}

