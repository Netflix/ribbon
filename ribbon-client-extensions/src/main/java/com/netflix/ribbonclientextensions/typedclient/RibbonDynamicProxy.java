package com.netflix.ribbonclientextensions.typedclient;

import io.reactivex.netty.protocol.http.client.HttpClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class RibbonDynamicProxy<O> implements InvocationHandler {
    private final Map<Method, MethodTemplateExecutor<O>> templateGeneratorMap;
    private final HttpClient httpClient;

    public RibbonDynamicProxy(Class<O> clientInterface, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.templateGeneratorMap = MethodTemplateExecutor.from(clientInterface);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodTemplateExecutor template = templateGeneratorMap.get(method);
        if (template != null) {
            return template.executeFromTemplate(httpClient, args);
        }
        // This must be one of the Object methods. Lets run it on the handler itself.
        return ReflectUtil.executeOnInstance(this, method, args);
    }

    @Override
    public String toString() {
        return "RibbonDynamicProxy{...}";
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> clientInterface, HttpClient httpClient) {
        if (!clientInterface.isInterface()) {
            throw new IllegalArgumentException(clientInterface.getName() + " is a class not interface");
        }
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{clientInterface},
                new RibbonDynamicProxy<T>(clientInterface, httpClient)
        );
    }
}
