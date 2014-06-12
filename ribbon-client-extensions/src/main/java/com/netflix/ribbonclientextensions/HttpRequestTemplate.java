package com.netflix.ribbonclientextensions;

import java.util.List;

import io.reactivex.netty.protocol.http.client.ContentSource;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RawContentSource;

import com.netflix.hystrix.HystrixCommandProperties.Setter;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

public class HttpRequestTemplate<I, O> implements RequestTemplate<I, O, HttpClientResponse<O>> {

    @Override
    public HttpRequestTemplate<I, O> withFallbackProvider(FallbackHandler<O> fallbackProvider) {
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
    public HttpRequestTemplate<I, O> withFallbackDeterminator(
            FallbackDeterminator<HttpClientResponse<O>> fallbackDeterminator) {
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
        return null;
    }

    @Override
    public HttpRequestTemplate<I, O> withCache(
            String cacheKeyTemplate, List<CacheProvider<O>> cacheProviders) {
        return this;
    }
}
