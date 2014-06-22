package com.netflix.ribbonclientextensions.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.ContentSource;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.RawContentSource;

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.ribbonclientextensions.RequestTemplate.RequestBuilder;
import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.template.ParsedTemplate;
import com.netflix.ribbonclientextensions.template.TemplateParser;
import com.netflix.ribbonclientextensions.template.TemplateParsingException;

public class HttpRequestBuilder<T> extends RequestBuilder<T> {

    private HttpRequestTemplate<T> requestTemplate;
    private HttpClient<ByteBuf, ByteBuf> client;
    private HystrixObservableCommand.Setter setter;
    private Map<String, Object> vars;
    private ParsedTemplate parsedUriTemplate;
    
    HttpRequestBuilder(HttpClient<ByteBuf, ByteBuf> client, HttpRequestTemplate<T> requestTemplate, HystrixObservableCommand.Setter setter) {
        this.requestTemplate = requestTemplate;
        this.client = client;
        this.setter = setter;
        this.parsedUriTemplate = requestTemplate.uriTemplate();
        vars = new ConcurrentHashMap<String, Object>();
    }
    
    RibbonHystrixObservableCommand<T> createHystrixCommand() {
        return new RibbonHystrixObservableCommand<T>(client, requestTemplate, this, setter);
    }

    @Override
    public HttpRequestBuilder<T> withRequestProperty(
            String key, Object value) {
        vars.put(key, value.toString());
        return this;
    }
    
    public HttpRequestBuilder<T> withContentSource(ContentSource<ByteBuf> source) {
        return this;
    }
    
    public HttpRequestBuilder<T> withRawContentSource(RawContentSource<?> raw) {
        return this;
    }


    @Override
    public RibbonRequest<T> build() {
        return new HttpRequest<T>(this);
    }
        
    HttpClientRequest<ByteBuf> createClientRequest() {
        String uri;
        try {
            uri = TemplateParser.toData(vars, parsedUriTemplate.getTemplate(), parsedUriTemplate.getParsed());
        } catch (TemplateParsingException e) {
            throw new HystrixBadRequestException("Problem parsing the URI template", e);
        }
        return HttpClientRequest.create(requestTemplate.method(), uri);
    }
    
    String cacheKey() {
        return requestTemplate.cacheKeyTemplate();
    }
    
    Map<String, Object> requestProperties() {
        return vars;
    }
}
