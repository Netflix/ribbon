package com.netflix.ribbonclientextensions.http;

import java.util.concurrent.Future;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.RequestWithMetaData;
import com.netflix.ribbonclientextensions.RibbonResponse;

class HttpRequest<T> implements RibbonRequest<T> {

    private HttpRequestBuilder<T> requestBuilder;

    HttpRequest(HttpRequestBuilder<T> requestBuilder) {
        this.requestBuilder = requestBuilder;
    }
    
    @Override
    public T execute() {
        return requestBuilder.createHystrixCommand().execute();
    }

    @Override
    public Future<T> queue() {
        return requestBuilder.createHystrixCommand().queue();
    }

    @Override
    public Observable<T> observe() {
        return requestBuilder.createHystrixCommand().observe();
    }

    @Override
    public Observable<T> toObservable() {
        return requestBuilder.createHystrixCommand().toObservable();
    }

    @Override
    public RequestWithMetaData<T> withMetadata() {
        return new HttpMetaRequest<T>(requestBuilder);
    }
    

}
