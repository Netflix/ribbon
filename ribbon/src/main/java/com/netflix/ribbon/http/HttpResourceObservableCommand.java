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
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.ribbon.ResponseValidator;
import com.netflix.ribbon.ServerError;
import com.netflix.ribbon.UnsuccessfulResponseException;
import com.netflix.ribbon.hystrix.FallbackHandler;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;

import java.util.Map;

public class HttpResourceObservableCommand<T> extends HystrixObservableCommand<T> {

    private final HttpClient<ByteBuf, ByteBuf> httpClient;
    private final HttpClientRequest<ByteBuf> httpRequest;
    private final String hystrixCacheKey;
    private final Map<String, Object> requestProperties;
    private final FallbackHandler<T> fallbackHandler;
    private final Class<? extends T> classType;
    private final ResponseValidator<HttpClientResponse<ByteBuf>> validator;

    public HttpResourceObservableCommand(HttpClient<ByteBuf, ByteBuf> httpClient,
                                         HttpClientRequest<ByteBuf> httpRequest, String hystrixCacheKey,
                                         Map<String, Object> requestProperties,
                                         FallbackHandler<T> fallbackHandler,
                                         ResponseValidator<HttpClientResponse<ByteBuf>> validator,
                                         Class<? extends T> classType,
                                         HystrixObservableCommand.Setter setter) {
        super(setter);
        this.httpClient = httpClient;
        this.fallbackHandler = fallbackHandler;
        this.validator = validator;
        this.httpRequest = httpRequest;
        this.hystrixCacheKey = hystrixCacheKey;
        this.classType = classType;
        this.requestProperties = requestProperties;
    }

    @Override
    protected String getCacheKey() {
        if (hystrixCacheKey == null) {
            return super.getCacheKey();
        } else {
            return hystrixCacheKey;
        }
    }

    @Override
    protected Observable<T> resumeWithFallback() {
        if (fallbackHandler == null) {
            return super.resumeWithFallback();
        } else {
            return fallbackHandler.getFallback(this, this.requestProperties);
        }
    }

    @Override
    protected Observable<T> construct() {
        Observable<HttpClientResponse<ByteBuf>> httpResponseObservable = httpClient.submit(httpRequest);
        if (validator != null) {
            httpResponseObservable = httpResponseObservable.map(new Func1<HttpClientResponse<ByteBuf>, HttpClientResponse<ByteBuf>>() {
                @Override
                public HttpClientResponse<ByteBuf> call(HttpClientResponse<ByteBuf> t1) {
                    try {
                        validator.validate(t1);
                    } catch (UnsuccessfulResponseException e) {
                        throw new HystrixBadRequestException("Unsuccessful response", e);
                    } catch (ServerError e) {
                        throw new RuntimeException(e);
                    }
                    return t1;
                }
            });
        }
        return httpResponseObservable.flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<T>>() {
            @Override
            public Observable<T> call(HttpClientResponse<ByteBuf> t1) {
                return t1.getContent().map(new Func1<ByteBuf, T>() {
                    @Override
                    public T call(ByteBuf t1) {
                        return classType.cast(t1);
                    }

                });
            }
        });
    }
}
