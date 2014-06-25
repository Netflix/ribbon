package com.netflix.ribbonclientextensions.http;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.Iterator;

import rx.Observable;
import rx.functions.Func1;

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.ribbonclientextensions.CacheProvider;
import com.netflix.ribbonclientextensions.ServerError;
import com.netflix.ribbonclientextensions.UnsuccessfulResponseException;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate.CacheProviderWithKeyTemplate;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;
import com.netflix.ribbonclientextensions.template.TemplateParser;
import com.netflix.ribbonclientextensions.template.TemplateParsingException;

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
        try {
            String key = requestBuilder.cacheKey();
            if (key == null) {
                return super.getCacheKey();
            } else {
                return key;
            }
        } catch (TemplateParsingException e) {
            return super.getCacheKey();            
        }
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
        final Iterator<CacheProviderWithKeyTemplate<T>> cacheProviders = requestTemplate.cacheProviders().iterator();
        Observable<T> cached = null;
        if (cacheProviders.hasNext()) {
            CacheProviderWithKeyTemplate<T> provider = cacheProviders.next();
            String cacheKey;
            try {
                cacheKey = TemplateParser.toData(requestBuilder.requestProperties(), provider.getKeyTemplate());
            } catch (TemplateParsingException e) {
                return Observable.error(e);
            }
            cached = provider.getProvider().get(cacheKey, requestBuilder.requestProperties());
            while (cacheProviders.hasNext()) {
                final Observable<T> nextCache = cacheProviders.next().getProvider().get(cacheKey, requestBuilder.requestProperties());
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
