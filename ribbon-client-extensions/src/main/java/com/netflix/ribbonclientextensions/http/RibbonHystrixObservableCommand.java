package com.netflix.ribbonclientextensions.http;

import java.util.Iterator;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.exception.*;
import com.netflix.ribbonclientextensions.CacheProvider;
import com.netflix.ribbonclientextensions.ServerError;
import com.netflix.ribbonclientextensions.UnsuccessfulResponseException;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

class RibbonHystrixObservableCommand<T> extends HystrixObservableCommand<T> {

    private HttpClient<ByteBuf, ByteBuf> httpClient;
    private HttpRequestTemplate<T> requestTemplate;
    private HttpRequestBuilder<T> requestBuilder;

    RibbonHystrixObservableCommand(
            HttpClient<ByteBuf, ByteBuf> httpClient,
            HttpRequestTemplate<T> requestTemplate,
            HttpRequestBuilder<T> requestBuilder, RibbonHystrixObservableCommand.Setter setter) {
        super(setter);
        this.httpClient = httpClient;
        this.requestTemplate = requestTemplate;
        this.requestBuilder = requestBuilder;
    }

    @Override
    protected String getCacheKey() {
        return requestBuilder.cacheKey();
    }
    
    @Override
    protected Observable<T> getFallback() {
        FallbackHandler<T> handler = requestTemplate.fallbackHandler();
        if (handler == null) {
            return super.getFallback();
        } else {
            return handler.getFallback(this, requestBuilder.requestProperties());
        }
    }

    @Override
    protected Observable<T> run() {
        final String cacheKey = requestBuilder.cacheKey();
        final Iterator<CacheProvider<T>> cacheProviders = requestTemplate.cacheProviders().iterator();
        Observable<T> cached = null;
        if (cacheProviders.hasNext()) {
            CacheProvider<T> provider = cacheProviders.next();
            cached = provider.get(cacheKey, requestBuilder.requestProperties());
            while (cacheProviders.hasNext()) {
                final Observable<T> nextCache = cacheProviders.next().get(cacheKey, requestBuilder.requestProperties());
                cached = cached.onErrorResumeNext(nextCache);
            }
        }
        HttpClientRequest<ByteBuf> request = requestBuilder.createClientRequest();
        Observable<HttpClientResponse<ByteBuf>> httpResponseObservable = httpClient.submit(request);
        if (requestTemplate.responseValidator() != null) {
            httpResponseObservable = httpResponseObservable.map(new Func1<HttpClientResponse<ByteBuf>, HttpClientResponse<ByteBuf>>(){
                @Override
                public HttpClientResponse<ByteBuf> call(HttpClientResponse<ByteBuf> t1) {
                    try {
                        requestTemplate.responseValidator().validate(t1);
                    } catch (UnsuccessfulResponseException e) {
                        throw new HystrixBadRequestException("Unsuccessful response", e);
                    } catch (ServerError e) {
                        throw new RuntimeException(e);
                    }
                    return t1;
                }
            });
        }
        Observable<T> httpEntities = httpResponseObservable.flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<T>>() {
                    @Override
                    public Observable<T> call(HttpClientResponse<ByteBuf> t1) {
                        return t1.getContent().map(new Func1<ByteBuf, T>(){
                            @Override
                            public T call(ByteBuf t1) {
                                return requestTemplate.getClassType().cast(t1);
                            }
                            
                        });
                    }
                });
        if (cached != null) {
            return cached.onErrorResumeNext(httpEntities);
        } else {
            return httpEntities;
        }
    }
}
