package com.netflix.ribbonclientextensions.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.netflix.client.netty.LoadBalancingRxClient;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.HystrixObservableCommand.Setter;
import com.netflix.ribbonclientextensions.CacheProvider;
import com.netflix.ribbonclientextensions.RequestTemplate;
import com.netflix.ribbonclientextensions.ResponseValidator;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;
import com.netflix.ribbonclientextensions.template.ParsedTemplate;

public class HttpRequestTemplate<T> implements RequestTemplate<T, HttpClientResponse<ByteBuf>> {

    private final HttpClient<ByteBuf, ByteBuf> client;
    private final String clientName;
    private final int maxResponseTime;
    private HystrixObservableCommand.Setter setter;
    private FallbackHandler<T> fallbackHandler;
    private ParsedTemplate parsedUriTemplate;
    private ResponseValidator<HttpClientResponse<ByteBuf>> validator;
    private HttpMethod method;
    private final String name;
    private final List<CacheProviderWithKeyTemplate<T>> cacheProviders;
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
            super();
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
    
    public HttpRequestTemplate(String name, HttpResourceGroup group, HttpClient<ByteBuf, ByteBuf> client, Class<? extends T> classType) {
        this.client = client;
        this.classType = classType;
        if (client instanceof LoadBalancingRxClient) {
            LoadBalancingRxClient<?, ? ,?> ribbonClient = (LoadBalancingRxClient<?, ? ,?>) client;
            maxResponseTime = ribbonClient.getResponseTimeOut();
            clientName = ribbonClient.getName();
            concurrentRequestLimit = ribbonClient.getMaxConcurrentRequests();
        } else {
            clientName = client.getClass().getName();
            maxResponseTime = -1;
            concurrentRequestLimit = -1;
        }
        this.name = name;
        this.group = group;
        method = HttpMethod.GET;
        headers = new DefaultHttpHeaders();
        headers.add(group.getHeaders());
        cacheProviders = new LinkedList<CacheProviderWithKeyTemplate<T>>();
        parsedTemplates = new HashMap<String, ParsedTemplate>();
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
            if (maxResponseTime > 0) {
                    setter.andCommandPropertiesDefaults(
                            HystrixCommandProperties.Setter().withExecutionIsolationThreadTimeoutInMilliseconds(maxResponseTime));
            }
            if (concurrentRequestLimit > 0) {
                setter.andCommandPropertiesDefaults(
                        HystrixCommandProperties.Setter().withExecutionIsolationSemaphoreMaxConcurrentRequests(concurrentRequestLimit));                
            }
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
    public HttpRequestTemplate<T> addCacheProvider(String keyTemplate, 
            CacheProvider<T> cacheProvider) {
        ParsedTemplate template = createParsedTemplate(keyTemplate);
        cacheProviders.add(new CacheProviderWithKeyTemplate<T>(template, cacheProvider));
        return this;
    }
        
    ParsedTemplate hystrixCacheKeyTemplate() {
        return hystrixCacheKeyTemplate;
    }
    
    List<CacheProviderWithKeyTemplate<T>> cacheProviders() {
        return cacheProviders;
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
        HttpRequestTemplate<T> newTemplate = new HttpRequestTemplate<T>(name, this.group, this.client, this.classType);
        newTemplate.cacheProviders.addAll(this.cacheProviders);
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
    
    HttpClient<ByteBuf, ByteBuf> getClient() {
        return this.client;
    }
}

