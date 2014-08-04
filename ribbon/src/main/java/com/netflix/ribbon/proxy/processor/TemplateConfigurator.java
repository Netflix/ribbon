package com.netflix.ribbon.proxy.processor;

import com.netflix.ribbon.http.HttpRequestTemplate;

/**
 * @author Tomasz Bak
 */
public interface TemplateConfigurator<T> {

    void configure(HttpRequestTemplate<T> template);

}
