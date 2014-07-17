package com.netflix.ribbon;

import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpRequestTemplateFactory;
import com.netflix.ribbon.http.HttpResourceGroup;

public class DefaultHttpRequestTemplateFactory implements HttpRequestTemplateFactory {
    @Override
    public <T> HttpRequestTemplate<T> newRequestTemplate(String name,
            HttpResourceGroup group, Class<? extends T> classType) {
        return new HttpRequestTemplate<T>(name, group, classType);
    }
}
