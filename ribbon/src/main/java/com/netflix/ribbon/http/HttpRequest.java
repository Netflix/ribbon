/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon.http;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import rx.Observable;
import rx.subjects.ReplaySubject;

import com.netflix.ribbon.CacheProvider;
import com.netflix.ribbon.RequestWithMetaData;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.template.TemplateParser;
import com.netflix.ribbon.template.TemplateParsingException;

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
    private final CacheProviderWithKey<T> cacheProvider;
    private final Map<String, Object> requestProperties;
    private final HttpClient<ByteBuf, ByteBuf> client;
    private final HttpRequestTemplate<T> template;

    HttpRequest(HttpRequestBuilder<T> requestBuilder) throws TemplateParsingException {
        this.client = requestBuilder.template().getClient();
        this.httpRequest = requestBuilder.createClientRequest();
        this.hystrixCacheKey = requestBuilder.hystrixCacheKey();
        this.requestProperties = new HashMap<String, Object>(requestBuilder.requestProperties());
        if (requestBuilder.cacheProvider() != null) {
            CacheProvider<T> provider = requestBuilder.cacheProvider().getProvider();
            String key = TemplateParser.toData(this.requestProperties, requestBuilder.cacheProvider().getKeyTemplate());
            this.cacheProvider = new CacheProviderWithKey<T>(provider, key);
        } else {
            this.cacheProvider = null;
        }
        this.template = requestBuilder.template();
        if (!ByteBuf.class.isAssignableFrom(template.getClassType())) {
            throw new IllegalArgumentException("Return type other than ByteBuf is not currently supported as serialization functionality is still work in progress");
        }
    }

    RibbonHystrixObservableCommand<T> createHystrixCommand() {
        return new RibbonHystrixObservableCommand<T>(client, httpRequest, hystrixCacheKey, cacheProvider, requestProperties, template.fallbackHandler(), 
                template.responseValidator(), template.getClassType(), template.hystrixProperties());
    }
    
    @Override
    public T execute() {
        return createHystrixCommand().getObservable().toBlocking().last();
    }

    @Override
    public Future<T> queue() {
        return createHystrixCommand().getObservable().toBlocking().toFuture();
    }

    @Override
    public Observable<T> observe() {
        ReplaySubject<T> subject = ReplaySubject.create();
        createHystrixCommand().getObservable().subscribe(subject);
        return subject;
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
