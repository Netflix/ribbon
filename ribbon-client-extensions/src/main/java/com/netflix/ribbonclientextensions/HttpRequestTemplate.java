package com.netflix.ribbonclientextensions;

import java.util.List;

import io.reactivex.netty.protocol.http.client.ContentSource;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RawContentSource;

import com.netflix.ribbonclientextensions.hystrix.FallbackProvider;
import com.netflix.ribbonclientextensions.interfaces.CacheProvider;

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
    public RequestTemplate<I, O, HttpClientResponse<O>> withCacheProvider(
            List<CacheProvider<O>> cacheProviders) {
        return this;
    }

    @Override
    public RequestTemplate<I, O, HttpClientResponse<O>> withFallbackDeterminator(
            FallbackDeterminator<HttpClientResponse<O>> fallbackDeterminator) {
        return this;
    }
}
