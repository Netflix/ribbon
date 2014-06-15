package com.netflix.ribbonclientextensions.http;

import java.util.List;

import rx.functions.Func1;

import io.reactivex.netty.protocol.http.client.ContentSource;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RawContentSource;

import com.netflix.hystrix.HystrixCommandProperties.Setter;
import com.netflix.ribbonclientextensions.CacheProvider;
import com.netflix.ribbonclientextensions.ResponseTransformer;
import com.netflix.ribbonclientextensions.RequestTemplate;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

public class HttpRequestTemplate<I, O> implements RequestTemplate<I, O, HttpClientResponse<O>> {

    private final HttpClient<I, O> client;
    
    public HttpRequestTemplate(HttpClient<I, O> client) {
        this.client = client;
    }
    
    @Override
    public HttpRequestTemplate<I, O> withFallbackProvider(FallbackHandler<O> fallbackProvider) {
        return this;
    }

    @Override
    public RequestBuilder<O> requestBuilder() {
        return new HttpRequestBuilder<I, O>(client, this);
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
    public HttpRequestTemplate<I, O> withHystrixCommandPropertiesDefaults(
            Setter setter) {
        return this;
    }

    @Override
    public HttpRequestTemplate<I, O> withHystrixThreadPoolPropertiesDefaults(
            com.netflix.hystrix.HystrixThreadPoolProperties.Setter setter) {
        return this;
    }

    @Override
    public HttpRequestTemplate<I, O> withHystrixCollapserPropertiesDefaults(
            com.netflix.hystrix.HystrixCollapserProperties.Setter setter) {
        return this;
    }

    @Override
    public HttpRequestTemplate<I, O> copy() {
        return this;
    }

    @Override
    public HttpRequestTemplate<I, O> withName(String name) {
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
    
    String name() {
        return null;
    }

    @Override
    public HttpRequestTemplate<I, O> withNetworkResponseTransformer(
            ResponseTransformer<HttpClientResponse<O>> transformer) {
        return this;
    }
}

