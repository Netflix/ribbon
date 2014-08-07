package com.netflix.ribbon.proxy.processor;

import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.http.HttpResponseValidator;
import com.netflix.ribbon.hystrix.FallbackHandler;
import com.netflix.ribbon.proxy.ProxyAnnotationException;
import com.netflix.ribbon.proxy.Utils;
import com.netflix.ribbon.proxy.annotation.Hystrix;

import java.lang.reflect.Method;

import static java.lang.String.format;

/**
 * @author Allen Wang
 */
public class HystrixAnnotationProcessor implements AnnotationProcessor {
    @Override
    public void process(HttpRequestTemplate template, Method method) {
        Hystrix annotation = method.getAnnotation(Hystrix.class);
        if (annotation == null) {
            return;
        }
        String cacheKey = annotation.cacheKey().trim();
        if (!cacheKey.isEmpty()) {
            template.withRequestCacheKey(cacheKey);
        }
        if (annotation.fallbackHandler().length == 1) {
            FallbackHandler<?> hystrixFallbackHandler = Utils.newInstance(annotation.fallbackHandler()[0]);
            template.withFallbackProvider(hystrixFallbackHandler);
        } else if (annotation.fallbackHandler().length > 1) {
            throw new ProxyAnnotationException(format("more than one fallback handler defined for method %s", method.getName()));
        }
        if (annotation.validator().length == 1) {
            HttpResponseValidator hystrixResponseValidator = Utils.newInstance(annotation.validator()[0]);
            template.withResponseValidator(hystrixResponseValidator);
        } else if (annotation.validator().length > 1) {
            throw new ProxyAnnotationException(format("more than one validator defined for method %s", method.getName()));
        }
    }

    @Override
    public void process(HttpResourceGroup group, Class interfaceClass) {
    }
}
