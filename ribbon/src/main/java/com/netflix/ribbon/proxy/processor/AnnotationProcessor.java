package com.netflix.ribbon.proxy.processor;

import com.netflix.ribbon.http.HttpRequestTemplate.Builder;
import com.netflix.ribbon.http.HttpResourceGroup;

import java.lang.reflect.Method;

/**
 * @author Tomasz Bak
 */
public interface AnnotationProcessor {

    void process(Builder templateBuilder, Method method);

    void process(HttpResourceGroup.Builder groupBuilder, Class interfaceClass);
}
