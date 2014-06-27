package com.netflix.ribbonclientextensions.http;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbonclientextensions.CacheProvider;
import com.netflix.ribbonclientextensions.ResponseValidator;
import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.RequestWithMetaData;
import com.netflix.ribbonclientextensions.RibbonResponse;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate.CacheProviderWithKeyTemplate;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;
import com.netflix.ribbonclientextensions.template.TemplateParser;
import com.netflix.ribbonclientextensions.template.TemplateParsingException;

class HttpRequest<T> implements RibbonRequest<T> {
    
    static class CacheProviderWithKey<T> {
        CacheProvider<T> cacheProvider;
        String key;
        public CacheProviderWithKey(CacheProvider<T> cacheProvider, String key) {
            super();
            this.cacheProvider = cacheProvider;
            this.key = key;
        }
        public final CacheProvider<T> getCacheProvider() {
            return cacheProvider;
        }
        public final String getKey() {
            return key;
        }
    }
    
    private final HttpClientRequest<ByteBuf> httpRequest;
    private final String hystrixCacheKey;
    private final List<CacheProviderWithKey<T>> cacheProviders;
    private final Map<String, Object> requestProperties;
    private final HttpClient<ByteBuf, ByteBuf> client;
    private final HttpRequestTemplate<T> template;

    HttpRequest(HttpRequestBuilder<T> requestBuilder) throws TemplateParsingException {
        this.client = requestBuilder.template().getClient();
        this.httpRequest = requestBuilder.createClientRequest();
        this.hystrixCacheKey = requestBuilder.hystrixCacheKey();
        this.requestProperties = new HashMap<String, Object>(requestBuilder.requestProperties());
        this.cacheProviders = new LinkedList<CacheProviderWithKey<T>>();
        this.template = requestBuilder.template();
        addCacheProviders(requestBuilder.cacheProviders());
    }

    private void addCacheProviders(List<CacheProviderWithKeyTemplate<T>> providers) throws TemplateParsingException {
        if (providers != null && providers.size() > 0) {
            for (CacheProviderWithKeyTemplate<T> cacheProviderWithTemplate: providers) {
                CacheProvider<T> provider = cacheProviderWithTemplate.getProvider();
                String key = TemplateParser.toData(this.requestProperties, cacheProviderWithTemplate.getKeyTemplate());
                cacheProviders.add(new CacheProviderWithKey<T>(provider, key));
            }
        }
    }
    
    RibbonHystrixObservableCommand<T> createHystrixCommand() {
        return new RibbonHystrixObservableCommand<T>(client, httpRequest, hystrixCacheKey, cacheProviders, requestProperties, template.fallbackHandler(), 
                template.responseValidator(), template.classType(), template.hystrixProperties());
    }
    
    @Override
    public T execute() {
        return createHystrixCommand().execute();
    }

    @Override
    public Future<T> queue() {
        return createHystrixCommand().queue();
    }

    @Override
    public Observable<T> observe() {
        return createHystrixCommand().observe();
    }

    @Override
    public Observable<T> toObservable() {
        return createHystrixCommand().toObservable();
    }

    @Override
    public RequestWithMetaData<T> withMetadata() {
        return new HttpMetaRequest<T>(this);
    }
    

}
