package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.RequestTemplate.RequestBuilder;
import com.netflix.ribbonclientextensions.Ribbon;
import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.ContentSource;
import io.reactivex.netty.protocol.http.client.ContentSource.SingletonSource;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.RawContentSource.SingletonRawSource;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class MethodTemplateExecutor<O> {

    private final MethodTemplate methodTemplate;

    public MethodTemplateExecutor(MethodTemplate methodTemplate) {
        this.methodTemplate = methodTemplate;
    }

    public <I> RibbonRequest<O> executeFromTemplate(HttpClient httpClient, Object[] args) {
        HttpRequestTemplate<ByteBuf, ByteBuf> httpRequestTemplate =
                Ribbon.newHttpRequestTemplate(methodTemplate.getTemplateName(), httpClient);
        httpRequestTemplate.withMethod(methodTemplate.getHttpMethod().name());
        if (methodTemplate.getPath() != null) {
            httpRequestTemplate.withUri(methodTemplate.getPath());
        }
        if(methodTemplate.getContentArgPosition() >= 0) {
            Object contentValue = args[methodTemplate.getContentArgPosition()];
            httpRequestTemplate.withContentSource(new SingletonSource(contentValue));
        }
        RequestBuilder requestBuilder = httpRequestTemplate.requestBuilder();
        int length = methodTemplate.getParamSize();
        for (int i = 0; i < length; i++) {
            String name = methodTemplate.getParamNames(i);
            Object value = args[methodTemplate.getParamPosition(i)];
            requestBuilder.withValue(name, value);
        }
        return requestBuilder.build();
    }

    public static <O> Map<Method, MethodTemplateExecutor<O>> from(Class clientInterface) {
        Map<Method, MethodTemplateExecutor<O>> tgm = new HashMap<Method, MethodTemplateExecutor<O>>();
        for (MethodTemplate mt : MethodTemplate.from(clientInterface)) {
            tgm.put(mt.getMethod(), new MethodTemplateExecutor(mt));
        }
        return tgm;
    }
}
