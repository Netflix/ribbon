package com.netflix.ribbonclientextensions;

import io.reactivex.netty.protocol.http.client.ContentSource;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RawContentSource;

import com.netflix.hystrix.HystrixCommandProperties.Setter;
import com.netflix.ribbonclientextensions.hystrix.FallbackProvider;

public class HttpRequestTemplate<I, O> implements RequestTemplate<I, O, HttpClientResponse<O>> {

    @Override
    public HttpRequestTemplate<I, O> withFallbackProvider(FallbackProvider<O> fallbackProvider) {
        return this;
    }

    @Override
    public RequestBuilder<O> requestBuilder() {
        return null;
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
    public RequestTemplate<I, O, HttpClientResponse<O>> withFallbackDeterminator(
            FallbackDeterminator<HttpClientResponse<O>> fallbackDeterminator) {
        return this;
    }

    @Override
    public RequestTemplate<I, O, HttpClientResponse<O>> addCacheProvider(
            CacheProvider<O> cacheProvider, String cacheKeyTemplate) {
        return this;
    }

    @Override
    public RequestTemplate<I, O, HttpClientResponse<O>> withHystrixCommandPropertiesDefaults(
            Setter setter) {
        return this;
    }

    @Override
    public RequestTemplate<I, O, HttpClientResponse<O>> withHystrixThreadPoolPropertiesDefaults(
            com.netflix.hystrix.HystrixThreadPoolProperties.Setter setter) {
        return this;
    }

    @Override
    public RequestTemplate<I, O, HttpClientResponse<O>> withHystrixCollapserPropertiesDefaults(
            com.netflix.hystrix.HystrixCollapserProperties.Setter setter) {
        return this;
    }
}
