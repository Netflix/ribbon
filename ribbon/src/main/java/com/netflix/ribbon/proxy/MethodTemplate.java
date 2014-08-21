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

import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.proxy.annotation.Content;
import com.netflix.ribbon.proxy.annotation.ContentTransformerClass;
import com.netflix.ribbon.proxy.annotation.TemplateName;
import com.netflix.ribbon.proxy.annotation.Var;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.channel.ContentTransformer;
import rx.Observable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

/**
 * Extracts information from Ribbon annotated method, to automatically populate the Ribbon request template.
 * A few validations are performed as well:
 * - a return type must be {@link com.netflix.ribbon.RibbonRequest}
 * - HTTP method must be always specified explicitly (there are no defaults)
 * - only one parameter with {@link com.netflix.ribbon.proxy.annotation.Content} annotation is allowed
 *
 * @author Tomasz Bak
 */
class MethodTemplate {

    private final Method method;
    private final String templateName;
    private final String[] paramNames;
    private final int[] valueIdxs;
    private final int contentArgPosition;
    private final Class<? extends ContentTransformer<?>> contentTansformerClass;
    private final Class<?> resultType;
    private final Class<?> genericContentType;

    MethodTemplate(Method method) {
        this.method = method;
        MethodAnnotationValues values = new MethodAnnotationValues(method);
        templateName = values.templateName;
        paramNames = values.paramNames;
        valueIdxs = values.valueIdxs;
        contentArgPosition = values.contentArgPosition;
        contentTansformerClass = values.contentTansformerClass;
        resultType = values.resultType;
        genericContentType = values.genericContentType;
    }

    public String getTemplateName() {
        return templateName;
    }

    public Method getMethod() {
        return method;
    }

    public String getParamName(int idx) {
        return paramNames[idx];
    }

    public int getParamPosition(int idx) {
        return valueIdxs[idx];
    }

    public int getParamSize() {
        return paramNames.length;
    }

    public int getContentArgPosition() {
        return contentArgPosition;
    }

    public Class<? extends ContentTransformer<?>> getContentTransformerClass() {
        return contentTansformerClass;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public Class<?> getGenericContentType() {
        return genericContentType;
    }

    public static <T> MethodTemplate[] from(Class<T> clientInterface) {
        List<MethodTemplate> list = new ArrayList<MethodTemplate>(clientInterface.getMethods().length);
        for (Method m : clientInterface.getMethods()) {
            list.add(new MethodTemplate(m));
        }
        return list.toArray(new MethodTemplate[list.size()]);
    }

    static class CacheProviderEntry {
        private final String key;
        private final com.netflix.ribbon.CacheProvider cacheProvider;

        CacheProviderEntry(String key, com.netflix.ribbon.CacheProvider cacheProvider) {
            this.key = key;
            this.cacheProvider = cacheProvider;
        }

        public String getKey() {
            return key;
        }

        public com.netflix.ribbon.CacheProvider getCacheProvider() {
            return cacheProvider;
        }
    }

    private static class MethodAnnotationValues {

        private final Method method;
        private String templateName;
        private String[] paramNames;
        private int[] valueIdxs;
        private int contentArgPosition;
        private Class<? extends ContentTransformer<?>> contentTansformerClass;
        private Class<?> resultType;
        private Class<?> genericContentType;

        private MethodAnnotationValues(Method method) {
            this.method = method;
            extractTemplateName();
            extractParamNamesWithIndexes();
            extractContentArgPosition();
            extractContentTransformerClass();
            extractResultType();
        }

        private void extractParamNamesWithIndexes() {
            List<String> nameList = new ArrayList<String>();
            List<Integer> idxList = new ArrayList<Integer>();
            Annotation[][] params = method.getParameterAnnotations();
            for (int i = 0; i < params.length; i++) {
                for (Annotation a : params[i]) {
                    if (a.annotationType().equals(Var.class)) {
                        String name = ((Var) a).value();
                        nameList.add(name);
                        idxList.add(i);
                    }
                }
            }
            int size = nameList.size();
            paramNames = new String[size];
            valueIdxs = new int[size];
            for (int i = 0; i < size; i++) {
                paramNames[i] = nameList.get(i);
                valueIdxs[i] = idxList.get(i);
            }
        }

        private void extractContentArgPosition() {
            Annotation[][] params = method.getParameterAnnotations();
            int pos = -1;
            int count = 0;
            for (int i = 0; i < params.length; i++) {
                for (Annotation a : params[i]) {
                    if (a.annotationType().equals(Content.class)) {
                        pos = i;
                        count++;
                    }
                }
            }
            if (count > 1) {
                throw new ProxyAnnotationException(format("Method %s annotates multiple parameters as @Content - at most one is allowed ", methodName()));
            }
            contentArgPosition = pos;
            if (contentArgPosition >= 0) {
                Type type = method.getGenericParameterTypes()[contentArgPosition];
                if (type instanceof ParameterizedType) {
                    ParameterizedType pType = (ParameterizedType) type;
                    if (pType.getActualTypeArguments() != null) {
                        genericContentType = (Class<?>) pType.getActualTypeArguments()[0];
                    }
                }
            }
        }

        private void extractContentTransformerClass() {
            ContentTransformerClass annotation = method.getAnnotation(ContentTransformerClass.class);
            if (contentArgPosition == -1) {
                if (annotation != null) {
                    throw new ProxyAnnotationException(format("ContentTransformClass defined on method %s with no @Content parameter", method.getName()));
                }
                return;
            }
            if (annotation == null) {
                Class<?> contentType = method.getParameterTypes()[contentArgPosition];
                if (Observable.class.isAssignableFrom(contentType) && ByteBuf.class.isAssignableFrom(genericContentType)
                        || ByteBuf.class.isAssignableFrom(contentType)
                        || byte[].class.isAssignableFrom(contentType)
                        || String.class.isAssignableFrom(contentType)) {
                    return;
                }
                throw new ProxyAnnotationException(format("ContentTransformerClass annotation missing for content type %s in method %s",
                        contentType.getName(), methodName()));
            }
            contentTansformerClass = annotation.value();
        }

        private void extractTemplateName() {
            TemplateName annotation = method.getAnnotation(TemplateName.class);
            if (null != annotation) {
                templateName = annotation.value();
            } else {
                templateName = method.getName();
            }
        }

        private void extractResultType() {
            Class<?> returnClass = method.getReturnType();
            if (!returnClass.isAssignableFrom(RibbonRequest.class)) {
                throw new ProxyAnnotationException(format("Method %s must return RibbonRequest<ByteBuf> type not %s",
                        methodName(), returnClass.getSimpleName()));
            }
            ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
            resultType = (Class<?>) returnType.getActualTypeArguments()[0];
            if (!ByteBuf.class.isAssignableFrom(resultType)) {
                throw new ProxyAnnotationException(format("Method %s must return RibbonRequest<ByteBuf> type; instead %s type parameter found",
                        methodName(), resultType.getSimpleName()));
            }
        }

        private String methodName() {
            return method.getDeclaringClass().getSimpleName() + '.' + method.getName();
        }
    }
}
