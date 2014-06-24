package com.netflix.ribbonclientextensions.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.ContentSource;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RawContentSource;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
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
    private ResponseValidator<HttpClientResponse<ByteBuf>> transformer;
    private HttpMethod method;
    private String name;
    private List<CacheProvider<T>> cacheProviders;
    private String cacheKeyTemplate;
    private Map<String, ParsedTemplate> parsedTemplates;
    private Class<? extends T> classType;
    
    public HttpRequestTemplate(String name, HttpResourceGroup group, HttpClient<ByteBuf, ByteBuf> client, Class<? extends T> classType) {
        this.client = client;
        this.classType = classType;
        if (client instanceof LoadBalancingRxClient) {
            LoadBalancingRxClient<?, ? ,?> ribbonClient = (LoadBalancingRxClient<?, ? ,?>) client;
            maxResponseTime = ribbonClient.getResponseTimeOut();
            clientName = ribbonClient.getName();
        } else {
            clientName = client.getClass().getName();
            maxResponseTime = -1;
        }
        this.name = name;
        // default method to GET
        method = HttpMethod.GET;
        cacheProviders = new LinkedList<CacheProvider<T>>();
        parsedTemplates = new HashMap<String, ParsedTemplate>();
    }
    
    @Override
    public HttpRequestTemplate<T> withFallbackProvider(FallbackHandler<T> fallbackHandler) {
        this.fallbackHandler = fallbackHandler;
        return this;
    }

    @Override
    public HttpRequestBuilder<T> requestBuilder() {
        // TODO: apply hystrix properties passed in to the template
        if (setter == null) {
            setter = HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(clientName))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(name()))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationThreadTimeoutInMilliseconds(maxResponseTime));
        }
        return new HttpRequestBuilder<T>(client, this, setter);
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
        return this;
    }    
    
    @Override
    public HttpRequestTemplate<T> withRequestCacheKey(
            String cacheKeyTemplate) {
        this.cacheKeyTemplate = cacheKeyTemplate;
        return this;
    }

    @Override
    public HttpRequestTemplate<T> addCacheProvider(String keyTemplate, 
            CacheProvider<T> cacheProvider) {
        this.cacheKeyTemplate = keyTemplate;
        cacheProviders.add(cacheProvider);
        return this;
    }
        
    String cacheKeyTemplate() {
        return cacheKeyTemplate;
    }
    
    List<CacheProvider<T>> cacheProviders() {
        return cacheProviders;
    }
    
    ResponseValidator<HttpClientResponse<ByteBuf>> responseValidator() {
        return transformer;
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
    
    @Override
    public String name() {
        return name;
    }
    
    @Override
    public HttpRequestTemplate<T> withResponseValidator(
            ResponseValidator<HttpClientResponse<ByteBuf>> validator) {
        this.transformer = validator;
        return this;
    }

    @Override
    public HttpRequestTemplate<T> copy(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public HttpRequestTemplate<T> withHystrixProperties(
            Setter propertiesSetter) {
        this.setter = propertiesSetter;
        return this;
    }
    
    
}

