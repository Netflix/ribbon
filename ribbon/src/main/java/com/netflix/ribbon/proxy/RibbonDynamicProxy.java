/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon.proxy;

import com.netflix.client.config.ClientConfigFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.DefaultResourceFactory;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.RibbonTransportFactory;
import com.netflix.ribbon.http.HttpResourceGroup;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class RibbonDynamicProxy<T> implements InvocationHandler {
    private final ProxyLifeCycle lifeCycle;
    private final Map<Method, MethodTemplateExecutor> templateExecutorMap;

    RibbonDynamicProxy(Class<T> clientInterface, HttpResourceGroup httpResourceGroup) {
        lifeCycle = new ProxyLifecycleImpl(httpResourceGroup);
        templateExecutorMap = MethodTemplateExecutor.from(httpResourceGroup, clientInterface);
    }

    public RibbonDynamicProxy(Class<T> clientInterface, RibbonResourceFactory resourceGroupFactory, ClientConfigFactory configFactory, RibbonTransportFactory transportFactory) {
        ClassTemplate<T> classTemplate = ClassTemplate.from(clientInterface);
        IClientConfig config =  createClientConfig(classTemplate, configFactory);
        HttpResourceGroup httpResourceGroup = new ProxyHttpResourceGroupFactory<T>(classTemplate, resourceGroupFactory, config, transportFactory).createResourceGroup();
        templateExecutorMap = MethodTemplateExecutor.from(httpResourceGroup, clientInterface);
        lifeCycle = new ProxyLifecycleImpl(httpResourceGroup);
    }

    private IClientConfig createClientConfig(ClassTemplate<T> classTemplate, ClientConfigFactory configFactory) {
        return configFactory.newConfig();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodTemplateExecutor template = templateExecutorMap.get(method);
        if (template != null) {
            return template.executeFromTemplate(args);
        }
        if (ProxyLifeCycle.class.isAssignableFrom(method.getDeclaringClass())) {
            return handleProxyLifeCycle(method, args);
        }
        // This must be one of the Object methods. Lets run it on the handler itself.
        return Utils.executeOnInstance(this, method, args);
    }

    private Object handleProxyLifeCycle(Method method, Object[] args) {
        try {
            return method.invoke(lifeCycle, args);
        } catch (Exception e) {
            throw new RibbonProxyException("ProxyLifeCycle call failure on method " + method.getName(), e);
        }
    }

    @Override
    public String toString() {
        return "RibbonDynamicProxy{...}";
    }

    private static class ProxyLifecycleImpl implements ProxyLifeCycle {

        private final HttpResourceGroup httpResourceGroup;

        private volatile boolean shutdownFlag;

        ProxyLifecycleImpl(HttpResourceGroup httpResourceGroup) {
            this.httpResourceGroup = httpResourceGroup;
        }

        @Override
        public boolean isShutDown() {
            return shutdownFlag;
        }

        @Override
        public synchronized void shutdown() {
            if (!shutdownFlag) {
                httpResourceGroup.getClient().shutdown();
                shutdownFlag = true;
            }
        }
    }

    public static <T> T newInstance(Class<T> clientInterface) {
        return newInstance(clientInterface, new DefaultResourceFactory(ClientConfigFactory.DEFAULT, RibbonTransportFactory.DEFAULT),
                ClientConfigFactory.DEFAULT, RibbonTransportFactory.DEFAULT);
    }
    
    @SuppressWarnings("unchecked")
    static <T> T newInstance(Class<T> clientInterface, HttpResourceGroup httpResourceGroup) {
        if (!clientInterface.isInterface()) {
            throw new IllegalArgumentException(clientInterface.getName() + " is a class not interface");
        }
        if (httpResourceGroup == null) {
            throw new NullPointerException("HttpResourceGroup is null");
        }
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{clientInterface, ProxyLifeCycle.class},
                new RibbonDynamicProxy<T>(clientInterface, httpResourceGroup)
        );
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> clientInterface, RibbonResourceFactory resourceGroupFactory, ClientConfigFactory configFactory, RibbonTransportFactory transportFactory) {
        if (!clientInterface.isInterface()) {
            throw new IllegalArgumentException(clientInterface.getName() + " is a class not interface");
        }
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{clientInterface, ProxyLifeCycle.class},
                new RibbonDynamicProxy<T>(clientInterface, resourceGroupFactory, configFactory, transportFactory)
        );
    }
}
