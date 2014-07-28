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

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbon.CacheProvider;
import com.netflix.ribbon.RequestWithMetaData;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.hystrix.CacheObservableCommand;
import com.netflix.ribbon.hystrix.HystrixObservableCommandChain;
import com.netflix.ribbon.template.TemplateParser;
import com.netflix.ribbon.template.TemplateParsingException;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import rx.Observable;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

class HttpRequest<T> implements RibbonRequest<T> {

    static class CacheProviderWithKey<T> {
        CacheProvider<T> cacheProvider;
        String key;

        public CacheProviderWithKey(CacheProvider<T> cacheProvider, String key) {
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

    private static final Func1<ByteBuf, ByteBuf> refCountIncrementer = new Func1<ByteBuf, ByteBuf>() {
        @Override
        public ByteBuf call(ByteBuf t1) {
            t1.retain();
            return t1;
        }
    };

    private final HttpClientRequest<ByteBuf> httpRequest;
    private final String hystrixCacheKey;
    private final String cacheHystrixCacheKey;
    private final CacheProviderWithKey<T> cacheProvider;
    private final Map<String, Object> requestProperties;
    private final HttpClient<ByteBuf, ByteBuf> client;
    /* package private for HttpMetaRequest */ final HttpRequestTemplate<T> template;

    HttpRequest(HttpRequestBuilder<T> requestBuilder) throws TemplateParsingException {
        client = requestBuilder.template().getClient();
        httpRequest = requestBuilder.createClientRequest();
        hystrixCacheKey = requestBuilder.hystrixCacheKey();
        cacheHystrixCacheKey = hystrixCacheKey == null ? null : hystrixCacheKey + HttpRequestTemplate.CACHE_HYSTRIX_COMMAND_SUFFIX;
        requestProperties = new HashMap<String, Object>(requestBuilder.requestProperties());
        if (requestBuilder.cacheProvider() != null) {
            CacheProvider<T> provider = requestBuilder.cacheProvider().getProvider();
            String key = TemplateParser.toData(this.requestProperties, requestBuilder.cacheProvider().getKeyTemplate());
            cacheProvider = new CacheProviderWithKey<T>(provider, key);
        } else {
            cacheProvider = null;
        }
        template = requestBuilder.template();
        if (!ByteBuf.class.isAssignableFrom(template.getClassType())) {
            throw new IllegalArgumentException("Return type other than ByteBuf is not currently supported as serialization functionality is still work in progress");
        }
    }

    HystrixObservableCommandChain<T> createHystrixCommandChain() {
        List<HystrixObservableCommand<T>> commands = new ArrayList<HystrixObservableCommand<T>>(2);
        if (cacheProvider != null) {
            commands.add(new CacheObservableCommand<T>(cacheProvider.getCacheProvider(), cacheProvider.getKey(), cacheHystrixCacheKey,
                    requestProperties, template.cacheHystrixProperties()));
        }
        commands.add(new HttpResourceObservableCommand<T>(client, httpRequest, hystrixCacheKey, requestProperties, template.fallbackHandler(),
                template.responseValidator(), template.getClassType(), template.hystrixProperties()));

        return new HystrixObservableCommandChain<T>(commands);
    }

    @Override
    public Observable<T> toObservable() {
        return createHystrixCommandChain().toObservable();
    }

    @Override
    public T execute() {
        return getObservable().toBlocking().last();
    }

    @Override
    public Future<T> queue() {
        return getObservable().toBlocking().toFuture();
    }

    @Override
    public Observable<T> observe() {
        ReplaySubject<T> subject = ReplaySubject.create();
        getObservable().subscribe(subject);
        return subject;
    }

    @Override
    public RequestWithMetaData<T> withMetadata() {
        return new HttpMetaRequest<T>(this);
    }

    boolean isByteBufResponse() {
        return ByteBuf.class.isAssignableFrom(template.getClassType());
    }

    Observable<T> getObservable() {
        if (isByteBufResponse()) {
            return ((Observable) toObservable()).map(refCountIncrementer);
        } else {
            return toObservable();
        }
    }
}
