package com.netflix.ribbonclientextensions.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

public class HttpRequestBuilder<I, O> extends RequestBuilder<O> {

    private HttpRequestTemplate<I, O> requestTemplate;
    private HttpClient<I, O> client;
    private HystrixObservableCommand.Setter setter;
    private Map<String, String> vars;
    private ParsedTemplate parsedUriTemplate;
    
    HttpRequestBuilder(HttpClient<I, O> client, HttpRequestTemplate<I, O> requestTemplate, HystrixObservableCommand.Setter setter) {
        this.requestTemplate = requestTemplate;
        this.client = client;
        this.setter = setter;
        this.parsedUriTemplate = requestTemplate.uriTemplate();
        vars = new ConcurrentHashMap<String, String>();
    }
    
    RibbonHystrixObservableCommand<I, O> createHystrixCommand() {
        return new RibbonHystrixObservableCommand<I, O>(client, requestTemplate, this, setter);
    }

    @Override
    public HttpRequestBuilder<I, O> withValue(
            String key, Object value) {
        vars.put(key, value.toString());
        return this;
    }
    
    public HttpRequestBuilder<I, O> withContentSource(ContentSource<I> source) {
        return this;
    }
    
    public HttpRequestBuilder<I, O> withRawContentSource(RawContentSource<?> raw) {
        return this;
    }


    @Override
    public RibbonRequest<O> build() {
        return new HttpRequest<I, O>(createHystrixCommand());
    }
    
    HttpClientRequest<I> createClientRequest() {
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
}
