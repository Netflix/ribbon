package com.netflix.ribbon.proxy.processor;

import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;

import java.lang.reflect.Method;

/**
 * @author Tomasz Bak
 */
public interface AnnotationProcessor {

    void process(HttpRequestTemplate template, Method method);

    void process(HttpResourceGroup group, Class interfaceClass);
}
