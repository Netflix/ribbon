package com.netflix.ribbon.proxy.processor;

import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.http.HttpRequestTemplate.Builder;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.ProxyAnnotationException;
import com.netflix.ribbon.proxy.annotation.Http;
import com.netflix.ribbon.proxy.annotation.Http.Header;
import com.netflix.ribbon.proxy.annotation.Http.HttpMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.lang.String.format;

/**
 * Http annotation
 */
public class HttpAnnotationProcessor implements AnnotationProcessor<HttpResourceGroup.Builder, Builder> {

    @Override
    public void process(String templateName, Builder templateBuilder, Method method) {
        Http annotation = method.getAnnotation(Http.class);
        if (null == annotation) {
            throw new ProxyAnnotationException(format("Method %s misses @Http annotation", method.getName()));
        }
        final HttpMethod httpMethod = annotation.method();
        final String uriTemplate = annotation.uri();
        final Map<String, List<String>> headers = annotation.headers().length == 0 ? null : new HashMap<String, List<String>>();
        for (Header h : annotation.headers()) {
            if (!headers.containsKey(h.name())) {
                ArrayList<String> values = new ArrayList<String>();
                values.add(h.value());
                headers.put(h.name(), values);
            } else {
                headers.get(h.name()).add(h.value());
            }
        }
        templateBuilder.withMethod(httpMethod.name());

        // uri
        if (uriTemplate != null) {
            templateBuilder.withUriTemplate(uriTemplate);
        }

        // headers
        if (headers != null) {
            for (Entry<String, List<String>> header : headers.entrySet()) {
                String key = header.getKey();
                for (String value : header.getValue()) {
                    templateBuilder.withHeader(key, value);
                }
            }
        }

    }

    @Override
    public void process(String groupName, HttpResourceGroup.Builder groupBuilder, RibbonResourceFactory resourceFactory, Class<?> interfaceClass) {
    }
}
