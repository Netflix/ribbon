package com.netflix.ribbon.proxy.processor;

import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;

/**
 * @author Tomasz Bak
 */
public interface TemplateFactory<T> {
    HttpRequestTemplate<T> createTemplate();
}
