package com.netflix.ribbonclientextensions.http;

import java.util.Iterator;
import java.util.List;

import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.ribbonclientextensions.CacheProvider;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

class RibbonHystrixObservableCommand<I, O> extends HystrixObservableCommand<O> {

    private HttpClient<I, O> httpClient;
    private HttpRequestTemplate<I, O> requestTemplate;
    private HttpRequestBuilder<I, O> requestBuilder;
    private RibbonHystrixObservableCommand.Setter setter;

    RibbonHystrixObservableCommand(
            HttpClient<I, O> httpClient,
            HttpRequestTemplate<I, O> requestTemplate,
            HttpRequestBuilder<I, O> requestBuilder, RibbonHystrixObservableCommand.Setter setter) {
        super(setter);
        this.httpClient = httpClient;
        this.requestTemplate = requestTemplate;
        this.requestBuilder = requestBuilder;
        this.setter = setter;
    }

    @Override
    protected String getCacheKey() {
        return requestBuilder.cacheKey();
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
        final String cacheKey = requestBuilder.cacheKey();
        final Iterator<CacheProvider<O>> cacheProviders = requestTemplate.cacheProviders().iterator();
        Observable<O> cached = null;
        if (cacheProviders.hasNext()) {
            CacheProvider<O> provider = cacheProviders.next();
            cached = provider.get(cacheKey);
            while (cacheProviders.hasNext()) {
                final Observable<O> nextCache = cacheProviders.next().get(cacheKey);
                cached = cached.onErrorResumeNext(nextCache);
            }
        }
        HttpClientRequest<I> request = requestBuilder.createClientRequest();
        Observable<HttpClientResponse<O>> httpResponseObservable = httpClient.submit(request);
        if (requestTemplate.responseTransformer() != null) {
            httpResponseObservable = httpResponseObservable.map(requestTemplate.responseTransformer());
        }
        Observable<O> httpEntities = httpResponseObservable.flatMap(new Func1<HttpClientResponse<O>, Observable<O>>() {
                    @Override
                    public Observable<O> call(HttpClientResponse<O> t1) {
                        return t1.getContent();
                    }
                });
        if (cached != null) {
            return cached.onErrorResumeNext(httpEntities);
        } else {
            return httpEntities;
        }
    }
}
