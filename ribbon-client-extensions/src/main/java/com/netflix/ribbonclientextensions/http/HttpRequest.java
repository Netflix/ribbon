package com.netflix.ribbonclientextensions.http;

import java.util.concurrent.Future;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.RequestWithMetaData;
import com.netflix.ribbonclientextensions.RibbonResponse;

class HttpRequest<I, O> implements RibbonRequest<O> {

    private HttpRequestBuilder<I, O> requestBuilder;

    HttpRequest(HttpRequestBuilder<I, O> requestBuilder) {
        this.requestBuilder = requestBuilder;
    }
    
    @Override
    public O execute() {
        return requestBuilder.createHystrixCommand().execute();
    }

    @Override
    public Future<O> queue() {
        return requestBuilder.createHystrixCommand().queue();
    }

    @Override
    public Observable<O> observe() {
        return requestBuilder.createHystrixCommand().observe();
    }

    @Override
    public Observable<O> toObservable() {
        return requestBuilder.createHystrixCommand().toObservable();
    }

    @Override
    public RequestWithMetaData<O> withMetadata() {
        return new HttpMetaRequest<I,O>(requestBuilder);
    }
    

}
