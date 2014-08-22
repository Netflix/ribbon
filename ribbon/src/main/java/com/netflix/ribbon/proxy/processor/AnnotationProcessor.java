package com.netflix.ribbon.proxy.processor;

import com.netflix.ribbon.ResourceGroup.GroupBuilder;
import com.netflix.ribbon.ResourceGroup.TemplateBuilder;
import com.netflix.ribbon.RibbonResourceFactory;

import java.lang.reflect.Method;

/**
 * @author Tomasz Bak
 */
public interface AnnotationProcessor<T extends GroupBuilder, S extends TemplateBuilder> {

    void process(String templateName, S templateBuilder, Method method);

    void process(String groupName, T groupBuilder, RibbonResourceFactory resourceFactory, Class<?> interfaceClass);
}
