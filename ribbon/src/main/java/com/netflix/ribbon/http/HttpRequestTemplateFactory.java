package com.netflix.ribbon.http;

/**
 * Creates the default HttpRequestTemplate.  For DI either bind to DefaultHttpRequestTemplateFactory
 * or implement your own HttpRequestTemplateFactory to customize or override HttpRequestTemplate
 * 
 * @author elandau
 *
 */
public interface HttpRequestTemplateFactory {
    public <T> HttpRequestTemplate<T> newRequestTemplate(String name, HttpResourceGroup group, Class<? extends T> classType);
}
