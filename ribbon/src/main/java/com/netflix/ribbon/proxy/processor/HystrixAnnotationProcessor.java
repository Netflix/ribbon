package com.netflix.ribbon.proxy.processor;

import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.http.HttpRequestTemplate.Builder;
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
public class HystrixAnnotationProcessor implements AnnotationProcessor<HttpResourceGroup.Builder, Builder> {
    @Override
    public void process(String templateName, Builder templateBuilder, Method method) {
        Hystrix annotation = method.getAnnotation(Hystrix.class);
        if (annotation == null) {
            return;
        }
        String cacheKey = annotation.cacheKey().trim();
        if (!cacheKey.isEmpty()) {
            templateBuilder.withRequestCacheKey(cacheKey);
        }
        if (annotation.fallbackHandler().length == 1) {
            FallbackHandler<?> hystrixFallbackHandler = Utils.newInstance(annotation.fallbackHandler()[0]);
            templateBuilder.withFallbackProvider(hystrixFallbackHandler);
        } else if (annotation.fallbackHandler().length > 1) {
            throw new ProxyAnnotationException(format("more than one fallback handler defined for method %s", method.getName()));
        }
        if (annotation.validator().length == 1) {
            HttpResponseValidator hystrixResponseValidator = Utils.newInstance(annotation.validator()[0]);
            templateBuilder.withResponseValidator(hystrixResponseValidator);
        } else if (annotation.validator().length > 1) {
            throw new ProxyAnnotationException(format("more than one validator defined for method %s", method.getName()));
        }
    }

    @Override
    public void process(String groupName, HttpResourceGroup.Builder groupBuilder, RibbonResourceFactory resourceFactory, Class<?> interfaceClass) {
    }
}
