package com.netflix.ribbon.proxy.processor;

import java.lang.reflect.Method;

/**
 * @author Tomasz Bak
 */
public interface AnnotationProcessor {

    TemplateConfigurator process(Method method);
}
