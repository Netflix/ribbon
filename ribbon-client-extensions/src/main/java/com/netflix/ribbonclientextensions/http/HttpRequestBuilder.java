package com.netflix.ribbonclientextensions.http;

import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;

import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbonclientextensions.RequestTemplate.RequestBuilder;
import com.netflix.ribbonclientextensions.RibbonRequest;

class HttpRequestBuilder<I, O> extends RequestBuilder<O> {

    private HttpRequestTemplate<I, O> requestTemplate;
    private HttpClient<I, O> client;
    private HystrixObservableCommand.Setter setter;
    
    HttpRequestBuilder(HttpClient<I, O> client, HttpRequestTemplate<I, O> requestTemplate, HystrixObservableCommand.Setter setter) {
        this.requestTemplate = requestTemplate;
        this.client = client;
        this.setter = setter;
    }
    
    RibbonHystrixObservableCommand<I, O> createHystrixCommand() {
        return new RibbonHystrixObservableCommand<I, O>(client, requestTemplate, this, setter);
    }

    @Override
    public RequestBuilder<O> withValue(
            String key, Object value) {
        return null;
    }

    @Override
    public RibbonRequest<O> build() {
        return new HttpRequest<I, O>(createHystrixCommand());
    }
    
    HttpClientRequest<I> createClientRequest() {
        return HttpClientRequest.create(requestTemplate.method(), requestTemplate.uriTemplate());
    }
    
    String cacheKey() {
        return requestTemplate.cacheKeyTemplate();
    }
}
