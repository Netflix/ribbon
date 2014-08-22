package com.netflix.ribbon.proxy.processor;

import com.netflix.ribbon.CacheProviderFactory;
import com.netflix.ribbon.ResourceGroup.GroupBuilder;
import com.netflix.ribbon.ResourceGroup.TemplateBuilder;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.proxy.Utils;
import com.netflix.ribbon.proxy.annotation.CacheProvider;

import java.lang.reflect.Method;

/**
 * @author Allen Wang
 */
public class CacheProviderAnnotationProcessor implements AnnotationProcessor<GroupBuilder, TemplateBuilder> {
    @Override
    public void process(String templateName, TemplateBuilder templateBuilder, Method method) {
        CacheProvider annotation = method.getAnnotation(CacheProvider.class);
        if (annotation != null) {
            CacheProviderFactory<?> factory = Utils.newInstance(annotation.provider());
            templateBuilder.withCacheProvider(annotation.key(), factory.createCacheProvider());
        }
    }

    @Override
    public void process(String groupName, GroupBuilder groupBuilder, RibbonResourceFactory resourceFactory, Class<?> interfaceClass) {
    }
}
