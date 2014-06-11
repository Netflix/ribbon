package com.netflix.client.netty;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.udp.client.UdpClient;

public final class RibbonTransport {
    
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
}

