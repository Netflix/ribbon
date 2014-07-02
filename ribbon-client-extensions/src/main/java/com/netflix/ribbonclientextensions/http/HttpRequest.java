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
package com.netflix.ribbonclientextensions.http;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import rx.Observable;

import com.netflix.ribbonclientextensions.CacheProvider;
import com.netflix.ribbonclientextensions.RequestWithMetaData;
import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate.CacheProviderWithKeyTemplate;
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
                template.responseValidator(), template.getClassType(), template.hystrixProperties());
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
