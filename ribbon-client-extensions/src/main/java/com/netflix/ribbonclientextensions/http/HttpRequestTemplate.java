package com.netflix.ribbonclientextensions.http;

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

public class HttpRequestTemplate<I, O> implements RequestTemplate<I, O, HttpClientResponse<O>> {

    private final HttpClient<I, O> client;
    private final String clientName;
    private final int maxResponseTime;
    private HystrixObservableCommand.Setter setter;
    private FallbackHandler<O> fallbackHandler;
    private ParsedTemplate parsedUriTemplate;
    private ResponseValidator<HttpClientResponse<O>> transformer;
    private HttpMethod method;
    private String name;
    private List<CacheProvider<O>> cacheProviders;
    private String cacheKeyTemplate;
    private Map<String, ParsedTemplate> parsedTemplates;
    
    public HttpRequestTemplate(String name, HttpClient<I, O> client) {
        this.client = client;
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
        cacheProviders = new LinkedList<CacheProvider<O>>();
        parsedTemplates = new HashMap<String, ParsedTemplate>();
    }
    
    @Override
    public HttpRequestTemplate<I, O> withFallbackProvider(FallbackHandler<O> fallbackHandler) {
        this.fallbackHandler = fallbackHandler;
        return this;
    }

    @Override
    public HttpRequestBuilder<I, O> requestBuilder() {
        // TODO: apply hystrix properties passed in to the template
        if (setter == null) {
            setter = HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(clientName))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(name()))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationThreadTimeoutInMilliseconds(maxResponseTime));
        }
        return new HttpRequestBuilder<I, O>(client, this, setter);
    }
    
    public HttpRequestTemplate<I, O> withMethod(String method) {
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
    
    public HttpRequestTemplate<I, O> withUri(String uri) {
        this.parsedUriTemplate = createParsedTemplate(uri);
        return this;
    }
    
    public HttpRequestTemplate<I, O> withHeader(String name, String value) {
        return this;
    }    
    
    @Override
    public HttpRequestTemplate<I, O> withHystrixCacheKey(
            String cacheKeyTemplate) {
        this.cacheKeyTemplate = cacheKeyTemplate;
        return this;
    }

    @Override
    public HttpRequestTemplate<I, O> addCacheProvider(String keyTemplate, 
            CacheProvider<O> cacheProvider) {
        this.cacheKeyTemplate = keyTemplate;
        cacheProviders.add(cacheProvider);
        return this;
    }
        
    String cacheKeyTemplate() {
        return cacheKeyTemplate;
    }
    
    List<CacheProvider<O>> cacheProviders() {
        return cacheProviders;
    }
    
    ResponseValidator<HttpClientResponse<O>> responseValidator() {
        return transformer;
    }
    
    FallbackHandler<O> fallbackHandler() {
        return fallbackHandler;
    }
    
    ParsedTemplate uriTemplate() {
        return parsedUriTemplate;
    }
    
    HttpMethod method() {
        return method;
    }
    
    @Override
    public String name() {
        return name;
    }
    
    @Override
    public HttpRequestTemplate<I, O> withResponseValidator(
            ResponseValidator<HttpClientResponse<O>> transformer) {
        this.transformer = transformer;
        return this;
    }

    @Override
    public HttpRequestTemplate<I, O> copy(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public HttpRequestTemplate<I, O> withHystrixProperties(
            Setter propertiesSetter) {
        this.setter = propertiesSetter;
        return this;
    }
    
    
}

