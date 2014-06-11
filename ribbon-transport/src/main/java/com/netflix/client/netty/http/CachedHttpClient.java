package com.netflix.client.netty.http;

import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.CachedRxClient;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

public class CachedHttpClient<I, O> extends CachedRxClient<HttpClientRequest<I>, HttpClientResponse<O>, HttpClient<I,O>> {

    public CachedHttpClient(
            ILoadBalancer lb,
            IClientConfig config,
            RetryHandler retryHandler,
            PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator) {
        super(lb, config, retryHandler, pipelineConfigurator);
    }

    @Override
    protected HttpClient<I, O> cacheLoadRxClient(Server server) {
        // TODO Auto-generated method stub
        return null;
    }

}
