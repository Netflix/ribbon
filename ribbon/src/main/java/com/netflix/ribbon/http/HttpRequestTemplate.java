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
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.HashMap;
import java.util.Map;

import com.netflix.client.netty.LoadBalancingRxClient;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.HystrixObservableCommand.Setter;
import com.netflix.ribbon.CacheProvider;
import com.netflix.ribbon.RequestTemplate;
import com.netflix.ribbon.ResponseValidator;
import com.netflix.ribbon.hystrix.FallbackHandler;
import com.netflix.ribbon.template.ParsedTemplate;

/**
 * Provides API to construct a request template for HTTP resource. 
 * <p>
 * <b>Note:</b> This class is not thread safe. It is advised that the template is created and
 * constructed in same thread at initialization of the application. Users can call {@link #requestBuilder()}
 * later on which returns a {@link RequestBuilder} which is thread safe. 
 *
 * @author Allen Wang
 *
 * @param <T> Type for the return Object of the Http resource
 */
public class HttpRequestTemplate<T> extends RequestTemplate<T, HttpClientResponse<ByteBuf>> {

    public static final String CACHE_HYSTRIX_COMMAND_SUFFIX = "_cache";
    public static final int DEFAULT_CACHE_TIMEOUT = 20;

    private final HttpClient<ByteBuf, ByteBuf> client;
    private final String clientName;
    private final int maxResponseTime;
    private HystrixObservableCommand.Setter setter;
    private final HystrixObservableCommand.Setter cacheSetter;
    private FallbackHandler<T> fallbackHandler;
    private ParsedTemplate parsedUriTemplate;
    private ResponseValidator<HttpClientResponse<ByteBuf>> validator;
    private HttpMethod method;
    private final String name;
    private CacheProviderWithKeyTemplate<T> cacheProvider;
    private ParsedTemplate hystrixCacheKeyTemplate;
    private Map<String, ParsedTemplate> parsedTemplates;
    private final Class<? extends T> classType;
    private final int concurrentRequestLimit;
    private final HttpHeaders headers;
    private final HttpResourceGroup group;

    static class CacheProviderWithKeyTemplate<T> {
        private ParsedTemplate keyTemplate;
        private CacheProvider<T> provider;
        public CacheProviderWithKeyTemplate(ParsedTemplate keyTemplate,
                CacheProvider<T> provider) {
            this.keyTemplate = keyTemplate;
            this.provider = provider;
        }
        public final ParsedTemplate getKeyTemplate() {
            return keyTemplate;
        }
        public final CacheProvider<T> getProvider() {
            return provider;
        }
    }

    public HttpRequestTemplate(String name, HttpResourceGroup group, Class<? extends T> classType) {
        this.client = group.getClient();
        this.classType = classType;
        clientName = client.name();
        if (client instanceof LoadBalancingRxClient) {
            LoadBalancingRxClient<?, ? ,?> ribbonClient = (LoadBalancingRxClient<?, ? ,?>) client;
            maxResponseTime = ribbonClient.getResponseTimeOut();
            concurrentRequestLimit = ribbonClient.getMaxConcurrentRequests();
        } else {
            maxResponseTime = -1;
            concurrentRequestLimit = -1;
        }
        this.name = name;
        this.group = group;
        headers = new DefaultHttpHeaders();
        headers.add(group.getHeaders());
        parsedTemplates = new HashMap<String, ParsedTemplate>();

        String cacheName = clientName + CACHE_HYSTRIX_COMMAND_SUFFIX;
        cacheSetter = HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(cacheName))
                .andCommandKey(HystrixCommandKey.Factory.asKey(cacheName));
        HystrixCommandProperties.Setter cacheCommandProps = HystrixCommandProperties.Setter();
        cacheCommandProps.withExecutionIsolationThreadTimeoutInMilliseconds(DEFAULT_CACHE_TIMEOUT);
        cacheSetter.andCommandPropertiesDefaults(cacheCommandProps);
    }

    @Override
    public HttpRequestTemplate<T> withFallbackProvider(FallbackHandler<T> fallbackHandler) {
        this.fallbackHandler = fallbackHandler;
        return this;
    }

    @Override
    public HttpRequestBuilder<T> requestBuilder() {
        if (setter == null) {
            setter = HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(clientName))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(name()));
            HystrixCommandProperties.Setter commandProps = HystrixCommandProperties.Setter();
            if (maxResponseTime > 0) {
               commandProps.withExecutionIsolationThreadTimeoutInMilliseconds(maxResponseTime);
            }
            if (concurrentRequestLimit > 0) {
                commandProps.withExecutionIsolationSemaphoreMaxConcurrentRequests(concurrentRequestLimit);
            }
            setter.andCommandPropertiesDefaults(commandProps);
        }
        return new HttpRequestBuilder<T>(this);
    }

    public HttpRequestTemplate<T> withMethod(String method) {
        this.method = HttpMethod.valueOf(method);
        return this;
    }

    private ParsedTemplate createParsedTemplate(String template) {
        ParsedTemplate parsedTemplate = parsedTemplates.get(template);
        if (parsedTemplate == null) {
            parsedTemplate = ParsedTemplate.create(template);
            parsedTemplates.put(template, parsedTemplate);
        }
        return parsedTemplate;
    }

    public HttpRequestTemplate<T> withUriTemplate(String uri) {
        this.parsedUriTemplate = createParsedTemplate(uri);
        return this;
    }

    public HttpRequestTemplate<T> withHeader(String name, String value) {
        headers.add(name, value);
        return this;
    }

    @Override
    public HttpRequestTemplate<T> withRequestCacheKey(
            String cacheKeyTemplate) {
        this.hystrixCacheKeyTemplate = createParsedTemplate(cacheKeyTemplate);
        return this;
    }

    @Override
    public HttpRequestTemplate<T> withCacheProvider(String keyTemplate,
            CacheProvider<T> cacheProvider) {
        ParsedTemplate template = createParsedTemplate(keyTemplate);
        this.cacheProvider = new CacheProviderWithKeyTemplate<T>(template, cacheProvider);
        return this;
    }

    ParsedTemplate hystrixCacheKeyTemplate() {
        return hystrixCacheKeyTemplate;
    }

    CacheProviderWithKeyTemplate<T> cacheProvider() {
        return cacheProvider;
    }

    ResponseValidator<HttpClientResponse<ByteBuf>> responseValidator() {
        return validator;
    }

    FallbackHandler<T> fallbackHandler() {
        return fallbackHandler;
    }

    ParsedTemplate uriTemplate() {
        return parsedUriTemplate;
    }

    HttpMethod method() {
        return method;
    }

    Class<? extends T> getClassType() {
        return this.classType;
    }

    HttpHeaders getHeaders() {
        return this.headers;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public HttpRequestTemplate<T> withResponseValidator(
            ResponseValidator<HttpClientResponse<ByteBuf>> validator) {
        this.validator = validator;
        return this;
    }

    @Override
    public HttpRequestTemplate<T> copy(String name) {
        HttpRequestTemplate<T> newTemplate = new HttpRequestTemplate<T>(name, this.group, this.classType);
        newTemplate.cacheProvider = this.cacheProvider;
        newTemplate.method = this.method;
        newTemplate.headers.add(this.headers);
        newTemplate.parsedTemplates.putAll(this.parsedTemplates);
        newTemplate.parsedUriTemplate = this.parsedUriTemplate;
        newTemplate.setter = setter;
        newTemplate.fallbackHandler = this.fallbackHandler;
        newTemplate.validator = this.validator;
        newTemplate.hystrixCacheKeyTemplate = this.hystrixCacheKeyTemplate;
        return newTemplate;
    }

    @Override
    public HttpRequestTemplate<T> withHystrixProperties(
            Setter propertiesSetter) {
        this.setter = propertiesSetter;
        return this;
    }

    Setter hystrixProperties() {
        return this.setter;
    }

    Setter cacheHystrixProperties() {
        return cacheSetter;
    }

    HttpClient<ByteBuf, ByteBuf> getClient() {
        return this.client;
    }
}
