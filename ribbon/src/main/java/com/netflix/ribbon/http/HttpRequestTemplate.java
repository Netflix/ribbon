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

import com.netflix.ribbon.transport.netty.LoadBalancingRxClient;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.HystrixObservableCommand.Setter;
import com.netflix.ribbon.CacheProvider;
import com.netflix.ribbon.RequestTemplate;
import com.netflix.ribbon.ResourceGroup.TemplateBuilder;
import com.netflix.ribbon.ResponseValidator;
import com.netflix.ribbon.hystrix.FallbackHandler;
import com.netflix.ribbon.template.ParsedTemplate;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.HashMap;
import java.util.Map;

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

    public static class Builder<T> extends TemplateBuilder<T, HttpClientResponse<ByteBuf>, HttpRequestTemplate<T>> {
        private String name;
        private HttpResourceGroup resourceGroup;
        private Class<? extends T> classType;
        private FallbackHandler<T> fallbackHandler;
        private HttpMethod method;
        private ParsedTemplate parsedUriTemplate;
        private ParsedTemplate cacheKeyTemplate;
        private CacheProviderWithKeyTemplate<T> cacheProvider;
        private HttpHeaders headers;
        private Setter setter;
        private Map<String, ParsedTemplate> parsedTemplates;
        private ResponseValidator<HttpClientResponse<ByteBuf>> validator;

        private Builder(String name, HttpResourceGroup resourceGroup, Class<? extends T> classType) {
            this.name = name;
            this.resourceGroup = resourceGroup;
            this.classType = classType;
            headers = new DefaultHttpHeaders();
            headers.add(resourceGroup.getHeaders());
            parsedTemplates = new HashMap<String, ParsedTemplate>();
        }

        private ParsedTemplate createParsedTemplate(String template) {
            ParsedTemplate parsedTemplate = parsedTemplates.get(template);
            if (parsedTemplate == null) {
                parsedTemplate = ParsedTemplate.create(template);
                parsedTemplates.put(template, parsedTemplate);
            }
            return parsedTemplate;
        }

        public static <T> Builder<T> newBuilder(String templateName, HttpResourceGroup group, Class<? extends T> classType) {
            return new Builder(templateName, group, classType);
        }

        @Override
        public Builder<T> withFallbackProvider(FallbackHandler<T> fallbackHandler) {
            this.fallbackHandler = fallbackHandler;
            return this;
        }

        @Override
        public Builder<T> withResponseValidator(ResponseValidator<HttpClientResponse<ByteBuf>> validator) {
            this.validator = validator;
            return this;
        }

        public Builder<T>  withMethod(String method) {
            this.method = HttpMethod.valueOf(method);
            return this;
        }

        public Builder<T> withUriTemplate(String uriTemplate) {
            this.parsedUriTemplate = createParsedTemplate(uriTemplate);
            return this;
        }

        @Override
        public Builder<T> withRequestCacheKey(String cacheKeyTemplate) {
            this.cacheKeyTemplate = createParsedTemplate(cacheKeyTemplate);
            return this;
        }

        @Override
        public Builder<T> withCacheProvider(String keyTemplate, CacheProvider<T> cacheProvider) {
            ParsedTemplate template = createParsedTemplate(keyTemplate);
            this.cacheProvider = new CacheProviderWithKeyTemplate<T>(template, cacheProvider);
            return this;
        }

        public  Builder<T> withHeader(String name, String value) {
            headers.add(name, value);
            return this;
        }

        @Override
        public Builder<T> withHystrixProperties(
                Setter propertiesSetter) {
            this.setter = propertiesSetter;
            return this;
        }

        public HttpRequestTemplate<T> build() {
            return new HttpRequestTemplate<T>(name, resourceGroup, classType, setter, method, headers, parsedUriTemplate, fallbackHandler, validator, cacheProvider, cacheKeyTemplate);
        }
    }

    private final HttpClient<ByteBuf, ByteBuf> client;
    private final int maxResponseTime;
    private final HystrixObservableCommand.Setter setter;
    private final HystrixObservableCommand.Setter cacheSetter;
    private final FallbackHandler<T> fallbackHandler;
    private final ParsedTemplate parsedUriTemplate;
    private final ResponseValidator<HttpClientResponse<ByteBuf>> validator;
    private final HttpMethod method;
    private final String name;
    private final CacheProviderWithKeyTemplate<T> cacheProvider;
    private final ParsedTemplate hystrixCacheKeyTemplate;
    private final Class<? extends T> classType;
    private final int concurrentRequestLimit;
    private final HttpHeaders headers;
    private final HttpResourceGroup group;

    public static class CacheProviderWithKeyTemplate<T> {
        private final ParsedTemplate keyTemplate;
        private final CacheProvider<T> provider;
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

    protected HttpRequestTemplate(String name, HttpResourceGroup group, Class<? extends T> classType, HystrixObservableCommand.Setter setter,
                        HttpMethod method, HttpHeaders headers, ParsedTemplate uriTemplate,
                        FallbackHandler<T> fallbackHandler, ResponseValidator<HttpClientResponse<ByteBuf>> validator, CacheProviderWithKeyTemplate<T> cacheProvider,
                        ParsedTemplate hystrixCacheKeyTemplate) {
        this.group = group;
        this.name = name;
        this.classType = classType;
        this.method = method;
        this.parsedUriTemplate = uriTemplate;
        this.fallbackHandler = fallbackHandler;
        this.validator = validator;
        this.cacheProvider = cacheProvider;
        this.hystrixCacheKeyTemplate = hystrixCacheKeyTemplate;
        this.client = group.getClient();
        this.headers = headers;
        if (client instanceof LoadBalancingRxClient) {
            LoadBalancingRxClient ribbonClient = (LoadBalancingRxClient) client;
            maxResponseTime = ribbonClient.getResponseTimeOut();
            concurrentRequestLimit = ribbonClient.getMaxConcurrentRequests();
        } else {
            maxResponseTime = -1;
            concurrentRequestLimit = -1;
        }
        String cacheName = name + CACHE_HYSTRIX_COMMAND_SUFFIX;
        cacheSetter = HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(cacheName))
                .andCommandKey(HystrixCommandKey.Factory.asKey(cacheName));
        HystrixCommandProperties.Setter cacheCommandProps = HystrixCommandProperties.Setter();
        cacheCommandProps.withExecutionIsolationThreadTimeoutInMilliseconds(DEFAULT_CACHE_TIMEOUT);
        cacheSetter.andCommandPropertiesDefaults(cacheCommandProps);
        if (setter != null) {
            this.setter = setter;
        } else {
            this.setter = HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(client.name()))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(name()));
            HystrixCommandProperties.Setter commandProps = HystrixCommandProperties.Setter();
            if (maxResponseTime > 0) {
                commandProps.withExecutionIsolationThreadTimeoutInMilliseconds(maxResponseTime);
            }
            if (concurrentRequestLimit > 0) {
                commandProps.withExecutionIsolationSemaphoreMaxConcurrentRequests(concurrentRequestLimit);
            }
            this.setter.andCommandPropertiesDefaults(commandProps);
        }
    }

    @Override
    public HttpRequestBuilder<T> requestBuilder() {
        return new HttpRequestBuilder<T>(this);
    }

    protected final ParsedTemplate hystrixCacheKeyTemplate() {
        return hystrixCacheKeyTemplate;
    }

    protected final CacheProviderWithKeyTemplate<T> cacheProvider() {
        return cacheProvider;
    }

    protected final ResponseValidator<HttpClientResponse<ByteBuf>> responseValidator() {
        return validator;
    }

    protected final FallbackHandler<T> fallbackHandler() {
        return fallbackHandler;
    }

    protected final ParsedTemplate uriTemplate() {
        return parsedUriTemplate;
    }

    protected final HttpMethod method() {
        return method;
    }

    protected final Class<? extends T> getClassType() {
        return this.classType;
    }

    protected final HttpHeaders getHeaders() {
        return this.headers;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public HttpRequestTemplate<T> copy(String name) {
        HttpRequestTemplate<T> newTemplate = new HttpRequestTemplate<T>(name, group, classType, setter, method, headers, parsedUriTemplate, fallbackHandler, validator, cacheProvider, hystrixCacheKeyTemplate);
        return newTemplate;
    }

    protected final Setter hystrixProperties() {
        return this.setter;
    }

    protected final Setter cacheHystrixProperties() {
        return cacheSetter;
    }

    protected final HttpClient<ByteBuf, ByteBuf> getClient() {
        return this.client;
    }
}
