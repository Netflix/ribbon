package com.netflix.ribbon.proxy.processor;

import com.netflix.ribbon.CacheProviderFactory;
import com.netflix.ribbon.http.HttpRequestTemplate.Builder;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.Utils;
import com.netflix.ribbon.proxy.annotation.CacheProvider;

import java.lang.reflect.Method;

/**
 * @author Allen Wang
 */
public class CacheProviderAnnotationProcessor implements AnnotationProcessor {
    @Override
    public void process(Builder templateBuilder, Method method) {
        CacheProvider annotation = method.getAnnotation(CacheProvider.class);
        if (annotation != null) {
            CacheProviderFactory<?> factory = Utils.newInstance(annotation.provider());
            templateBuilder.withCacheProvider(annotation.key(), factory.createCacheProvider());
        }
    }

    @Override
    public void process(HttpResourceGroup.Builder groupBuilder, Class interfaceClass) {
    }
}
