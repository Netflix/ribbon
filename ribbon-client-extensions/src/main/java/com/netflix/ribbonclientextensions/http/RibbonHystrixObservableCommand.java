package com.netflix.ribbonclientextensions.http;

import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;

import com.netflix.hystrix.HystrixObservableCommand;

public class RibbonHystrixObservableCommand<I, O> extends HystrixObservableCommand<O> {

    private HttpClient<I, O> httpClient;
    private HttpRequestTemplate<I, O> requestTemplate;
    private HttpRequestBuilder<I, O> requestBuilder;

    public RibbonHystrixObservableCommand(
            HttpClient<I, O> httpClient,
            HttpRequestTemplate<I, O> requestTemplate,
            HttpRequestBuilder<I, O> requestBuilder, RibbonHystrixObservableCommand.Setter setter) {
        super(setter);
        this.httpClient = httpClient;
        this.requestTemplate = requestTemplate;
        this.requestBuilder = requestBuilder;
    }

    @Override
    protected String getCacheKey() {
        // TODO: should be from builder
        return requestTemplate.cacheKey();
    }

    @Override
    protected Observable<O> run() {
        HttpClientRequest<I> request = requestBuilder.createClientRequest();
        // TODO: add filtering of response and trigger Hystrix fallback
        return httpClient.submit(request)
                .flatMap(new Func1<HttpClientResponse<O>, Observable<O>>() {
                    @Override
                    public Observable<O> call(HttpClientResponse<O> t1) {
                        return t1.getContent();
                    }
                });
    }
    
    
}
