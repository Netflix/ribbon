package com.netflix.ribbonclientextensions;

import com.netflix.ribbonclientextensions.http.HttpRequestTemplate;

import com.netflix.ribbonclientextensions.typedclient.RibbonDynamicProxy;
import io.reactivex.netty.protocol.http.client.HttpClient;

public final class Ribbon {
    
    private Ribbon() {
    }
 
    public static <I, O> HttpRequestTemplate<I, O> newHttpRequestTemplate(String templateName, HttpClient<I, O> transportClient) {
        return new HttpRequestTemplate<I, O>(templateName, transportClient);
    }
 
    public static <I, O, T> T from(Class<T> contract, HttpClient<I, O> transportClient) {
        return RibbonDynamicProxy.newInstance(contract, transportClient);
    } 
}
