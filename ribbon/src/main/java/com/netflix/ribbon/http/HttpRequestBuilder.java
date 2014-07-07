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
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.RawContentSource;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.RequestTemplate.RequestBuilder;
import com.netflix.ribbon.http.HttpRequestTemplate.CacheProviderWithKeyTemplate;
import com.netflix.ribbon.template.ParsedTemplate;
import com.netflix.ribbon.template.TemplateParser;
import com.netflix.ribbon.template.TemplateParsingException;

public class HttpRequestBuilder<T> extends RequestBuilder<T> {

    private final HttpRequestTemplate<T> requestTemplate;
    private final Map<String, Object> vars;
    private final ParsedTemplate parsedUriTemplate;
    private RawContentSource<?> rawContentSource;
    
    HttpRequestBuilder(HttpRequestTemplate<T> requestTemplate) {
        this.requestTemplate = requestTemplate;
        this.parsedUriTemplate = requestTemplate.uriTemplate();
        vars = new ConcurrentHashMap<String, Object>();
    }
    
    @Override
    public HttpRequestBuilder<T> withRequestProperty(
            String key, Object value) {
        vars.put(key, value);
        return this;
    }
        
    public HttpRequestBuilder<T> withRawContentSource(RawContentSource<?> raw) {
        this.rawContentSource = raw;
        return this;
    }

    @Override
    public RibbonRequest<T> build() {
        if (requestTemplate.uriTemplate() == null) {
            throw new IllegalArgumentException("URI template is not defined");
        }
        if (requestTemplate.method() == null) {
            throw new IllegalArgumentException("HTTP method is not defined");
        }
        try {
            return new HttpRequest<T>(this);
        } catch (TemplateParsingException e) {
            throw new IllegalArgumentException(e);
        }
    }
        
    HttpClientRequest<ByteBuf> createClientRequest() {
        String uri;
        try {
            uri = TemplateParser.toData(vars, parsedUriTemplate.getTemplate(), parsedUriTemplate.getParsed());
        } catch (TemplateParsingException e) {
            throw new HystrixBadRequestException("Problem parsing the URI template", e);
        }
        HttpClientRequest<ByteBuf> request =  HttpClientRequest.create(requestTemplate.method(), uri);
        for (Map.Entry<String, String> entry: requestTemplate.getHeaders().entries()) {
            request.withHeader(entry.getKey(), entry.getValue());
        }
        if (rawContentSource != null) {
            request.withRawContentSource(rawContentSource);
        }
        return new RepeatableContentHttpRequest<ByteBuf>(request);
    }
    
    String hystrixCacheKey() throws TemplateParsingException {
        ParsedTemplate keyTemplate = requestTemplate.hystrixCacheKeyTemplate();
        if (keyTemplate == null || 
                (keyTemplate.getTemplate() == null || keyTemplate.getTemplate().length() == 0)) {
            return null;
        }
        return TemplateParser.toData(vars, requestTemplate.hystrixCacheKeyTemplate());
    }
     
    Map<String, Object> requestProperties() {
        return vars;
    }
    
    CacheProviderWithKeyTemplate<T> cacheProvider() {
        return requestTemplate.cacheProvider();
    }
    
    HttpRequestTemplate<T> template() {
        return requestTemplate;
    }
}
