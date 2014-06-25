package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.http.HttpResourceGroup;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class RibbonDynamicProxy<T> implements InvocationHandler {
    private final Class<T> clientInterface;
    private final Map<Method, MethodTemplateExecutor<T>> templateGeneratorMap;
    private final HttpResourceGroup httpResourceGroup;
    private final ClassTemplate classTemplate;

    public RibbonDynamicProxy(Class<T> clientInterface, HttpResourceGroup httpResourceGroup) {
        this.clientInterface = clientInterface;
        this.classTemplate = ClassTemplate.from(clientInterface);
        if (httpResourceGroup == null) {
            this.httpResourceGroup = new HttpResourceGroupFactory(this.classTemplate).createResourceGroup();
        } else {
            this.httpResourceGroup = httpResourceGroup;
        }
        this.templateGeneratorMap = MethodTemplateExecutor.from(clientInterface);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodTemplateExecutor template = templateGeneratorMap.get(method);
        if (template != null) {
            return template.executeFromTemplate(httpResourceGroup, args);
        }
        // This must be one of the Object methods. Lets run it on the handler itself.
        return ReflectUtil.executeOnInstance(this, method, args);
    }

    @Override
    public String toString() {
        return "RibbonDynamicProxy{...}";
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> clientInterface, HttpResourceGroup httpResourceGroup) {
        if (!clientInterface.isInterface()) {
            throw new IllegalArgumentException(clientInterface.getName() + " is a class not interface");
        }
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{clientInterface},
                new RibbonDynamicProxy<T>(clientInterface, httpResourceGroup)
        );
    }
}
