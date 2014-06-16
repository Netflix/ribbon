package com.netflix.ribbonclientextensions.http;

import io.reactivex.netty.protocol.http.client.ContentSource;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RawContentSource;

import java.util.List;

import com.netflix.client.netty.LoadBalancingRxClient;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.HystrixObservableCommand.Setter;
import com.netflix.ribbonclientextensions.CacheProvider;
import com.netflix.ribbonclientextensions.RequestTemplate;
import com.netflix.ribbonclientextensions.ResponseTransformer;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

public class HttpRequestTemplate<I, O> implements RequestTemplate<I, O, HttpClientResponse<O>> {

    private final HttpClient<I, O> client;
    private final String clientName;
    private final int maxResponseTime;
    private HystrixObservableCommand.Setter setter;
    
    public HttpRequestTemplate(HttpClient<I, O> client) {
        this.client = client;
        if (client instanceof LoadBalancingRxClient) {
            LoadBalancingRxClient<?, ? ,?> ribbonClient = (LoadBalancingRxClient<?, ? ,?>) client;
            maxResponseTime = ribbonClient.getResponseTimeOut();
            clientName = ribbonClient.getName();
        } else {
            clientName = client.getClass().getName();
            maxResponseTime = -1;
        }
    }
    
    @Override
    public HttpRequestTemplate<I, O> withFallbackProvider(FallbackHandler<O> fallbackProvider) {
        return this;
    }

    @Override
    public RequestBuilder<O> requestBuilder() {
        // TODO: apply hystrix properties passed in to the template
        if (setter == null) {
            setter = HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(clientName))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(name()))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationThreadTimeoutInMilliseconds(maxResponseTime));
        }
        return new HttpRequestBuilder<I, O>(client, this, setter);
    }
    
    public HttpRequestTemplate<I, O> withUri(String uri) {
        return this;
    }
    
    public HttpRequestTemplate<I, O> withHeader(String name, String value) {
        return this;
    }    
    
    public HttpRequestTemplate<I, O> withContentSource(ContentSource<I> source) {
        return this;
    }
    
    public HttpRequestTemplate<I, O> withRawContentSource(RawContentSource<?> raw) {
        return this;
    }

    @Override
    public HttpRequestTemplate<I, O> withCacheKey(
            String cacheKeyTemplate) {
        return this;
    }

    @Override
    public HttpRequestTemplate<I, O> addCacheProvider(
            CacheProvider<O> cacheProvider) {
        return this;
    }
        
    String cacheKey() {
        return null;
    }
    
    List<CacheProvider<O>> cacheProviders() {
        return null;
    }
    
    @Override
    public String name() {
        return null;
    }

    @Override
    public HttpRequestTemplate<I, O> withNetworkResponseTransformer(
            ResponseTransformer<HttpClientResponse<O>> transformer) {
        return this;
    }

    @Override
    public HttpRequestTemplate<I, O> copy(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public HttpRequestTemplate<I, O> withHystrixProperties(
            Setter propertiesSetter) {
        // TODO Auto-generated method stub
        return null;
    }
}

