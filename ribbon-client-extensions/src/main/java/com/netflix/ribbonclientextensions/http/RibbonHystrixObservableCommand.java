package com.netflix.ribbonclientextensions.http;

import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

class RibbonHystrixObservableCommand<I, O> extends HystrixObservableCommand<O> {

    private HttpClient<I, O> httpClient;
    private HttpRequestTemplate<I, O> requestTemplate;
    private HttpRequestBuilder<I, O> requestBuilder;

    RibbonHystrixObservableCommand(
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
    protected Observable<O> getFallback() {
        FallbackHandler<O> handler = requestTemplate.fallbackHandler();
        if (handler == null) {
            return super.getFallback();
        } else {
            return handler.call(this);
        }
    }

    @Override
    protected Observable<O> run() {
        HttpClientRequest<I> request = requestBuilder.createClientRequest();
        Observable<HttpClientResponse<O>> httpResponseObservable = httpClient.submit(request);
        if (requestTemplate.responseTransformer() != null) {
            httpResponseObservable = httpResponseObservable.map(requestTemplate.responseTransformer());
        }
        return httpResponseObservable.flatMap(new Func1<HttpClientResponse<O>, Observable<O>>() {
                    @Override
                    public Observable<O> call(HttpClientResponse<O> t1) {
                        return t1.getContent();
                    }
                });
    }
}
