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

import com.netflix.evcache.EVCacheTranscoder;
import com.netflix.ribbon.CacheProvider;
import com.netflix.ribbon.CacheProviderFactory;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.evache.EvCacheOptions;
import com.netflix.ribbon.http.HttpResponseValidator;
import com.netflix.ribbon.hystrix.FallbackHandler;
import com.netflix.ribbon.proxy.annotation.CacheProviders;
import com.netflix.ribbon.proxy.annotation.Content;
import com.netflix.ribbon.proxy.annotation.ContentTransformerClass;
import com.netflix.ribbon.proxy.annotation.EvCache;
import com.netflix.ribbon.proxy.annotation.Http;
import com.netflix.ribbon.proxy.annotation.Hystrix;
import com.netflix.ribbon.proxy.annotation.TemplateName;
import com.netflix.ribbon.proxy.annotation.Var;
import com.netflix.ribbon.proxy.annotation.CacheProviders.Provider;
import com.netflix.ribbon.proxy.annotation.Http.Header;
import com.netflix.ribbon.proxy.annotation.Http.HttpMethod;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.RawContentSource;
import io.reactivex.netty.serialization.ContentTransformer;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.*;

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
    private final Http.HttpMethod httpMethod;
    private final String uriTemplate;
    private final Map<String, List<String>> headers;
    private final String[] paramNames;
    private final int[] valueIdxs;
    private final int contentArgPosition;
    private final Class<? extends ContentTransformer<?>> contentTansformerClass;
    private final Class<?> resultType;
    private final String hystrixCacheKey;
    private final FallbackHandler<?> hystrixFallbackHandler;
    private final HttpResponseValidator hystrixResponseValidator;
    private final List<CacheProviderEntry> cacheProviders;
    private final EvCacheOptions evCacheOptions;

    MethodTemplate(Method method) {
        this.method = method;
        MethodAnnotationValues values = new MethodAnnotationValues(method);
        templateName = values.templateName;
        httpMethod = values.httpMethod;
        uriTemplate = values.uriTemplate;
        headers = Collections.unmodifiableMap(values.headers);
        paramNames = values.paramNames;
        valueIdxs = values.valueIdxs;
        contentArgPosition = values.contentArgPosition;
        contentTansformerClass = values.contentTansformerClass;
        resultType = values.resultType;
        hystrixCacheKey = values.hystrixCacheKey;
        hystrixFallbackHandler = values.hystrixFallbackHandler;
        hystrixResponseValidator = values.hystrixResponseValidator;
        cacheProviders = Collections.unmodifiableList(values.cacheProviders);
        evCacheOptions = values.evCacheOptions;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public String getTemplateName() {
        return templateName;
    }

    public Method getMethod() {
        return method;
    }

    public String getUriTemplate() {
        return uriTemplate;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
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

    public String getHystrixCacheKey() {
        return hystrixCacheKey;
    }

    public FallbackHandler<?> getHystrixFallbackHandler() {
        return hystrixFallbackHandler;
    }

    public HttpResponseValidator getHystrixResponseValidator() {
        return hystrixResponseValidator;
    }

    public List<CacheProviderEntry> getCacheProviders() {
        return cacheProviders;
    }

    public EvCacheOptions getEvCacheOptions() {
        return evCacheOptions;
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
        private final CacheProvider<?> cacheProvider;

        CacheProviderEntry(String key, CacheProvider<?> cacheProvider) {
            this.key = key;
            this.cacheProvider = cacheProvider;
        }

        public String getKey() {
            return key;
        }

        public CacheProvider<?> getCacheProvider() {
            return cacheProvider;
        }
    }

    private static class MethodAnnotationValues {

        private final Method method;
        private String templateName;
        private Http.HttpMethod httpMethod;
        private String uriTemplate;
        private String[] paramNames;
        private int[] valueIdxs;
        private int contentArgPosition;
        private Class<? extends ContentTransformer<?>> contentTansformerClass;
        private Class<?> resultType;
        public String hystrixCacheKey;
        private FallbackHandler<?> hystrixFallbackHandler;
        private HttpResponseValidator hystrixResponseValidator;
        public final Map<String, List<String>> headers = new HashMap<String, List<String>>();
        private final List<CacheProviderEntry> cacheProviders = new ArrayList<CacheProviderEntry>();
        private EvCacheOptions evCacheOptions;

        private MethodAnnotationValues(Method method) {
            this.method = method;
            extractTemplateName();
            extractHttpAnnotation();
            extractParamNamesWithIndexes();
            extractContentArgPosition();
            extractContentTransformerClass();
            extractResultType();
            extractHystrixHandlers();
            extractCacheProviders();
            extractEvCacheOptions();
        }

        private void extractCacheProviders() {
            CacheProviders annotation = method.getAnnotation(CacheProviders.class);
            if (annotation != null) {
                if (annotation.value().length > 1) {
                    throw new ProxyAnnotationException(format("more than one cache provider defined for method %s", methodName()));                    
                }
                for (Provider provider : annotation.value()) {
                    Class<? extends CacheProviderFactory<?>> providerClass = provider.provider();
                    CacheProviderFactory<?> factory = Utils.newInstance(providerClass);
                    cacheProviders.add(new CacheProviderEntry(provider.key(), factory.createCacheProvider()));
                }
            }
        }

        private void extractHystrixHandlers() {
            Hystrix annotation = method.getAnnotation(Hystrix.class);
            if (annotation == null) {
                return;
            }
            String cacheKey = annotation.cacheKey().trim();
            if (!cacheKey.isEmpty()) {
                hystrixCacheKey = cacheKey;
            }
            if (annotation.fallbackHandler().length == 1) {
                hystrixFallbackHandler = Utils.newInstance(annotation.fallbackHandler()[0]);
            } else if(annotation.fallbackHandler().length > 1) {
                throw new ProxyAnnotationException(format("more than one fallback handler defined for method %s", methodName()));
            }
            if (annotation.validator().length == 1) {
                hystrixResponseValidator = Utils.newInstance(annotation.validator()[0]);
            } else if(annotation.validator().length > 1) {
                throw new ProxyAnnotationException(format("more than one validator defined for method %s", methodName()));
            }
        }

        private void extractHttpAnnotation() {
            Http annotation = method.getAnnotation(Http.class);
            if (null == annotation) {
                throw new ProxyAnnotationException(format("Method %s misses @Http annotation", methodName()));
            }
            httpMethod = annotation.method();
            uriTemplate = annotation.uriTemplate();
            for (Header h : annotation.headers()) {
                if (!headers.containsKey(h.name())) {
                    ArrayList<String> values = new ArrayList<String>();
                    values.add(h.value());
                    headers.put(h.name(), values);
                } else {
                    headers.get(h.name()).add(h.value());
                }
            }
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
                if (RawContentSource.class.isAssignableFrom(contentType)
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
            }
        }

        private void extractResultType() {
            Class<?> returnClass = method.getReturnType();
            if (!returnClass.isAssignableFrom(RibbonRequest.class)) {
                throw new ProxyAnnotationException(format("Method %s must return Void or RibbonRequest<T> type not %s",
                        methodName(), returnClass.getSimpleName()));
            }
            ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
            resultType = (Class<?>) returnType.getActualTypeArguments()[0];
        }

        private void extractEvCacheOptions() {
            EvCache annotation = method.getAnnotation(EvCache.class);
            if (annotation == null) {
                return;
            }

            Class<? extends EVCacheTranscoder<?>>[] transcoderClasses = annotation.transcoder();
            EVCacheTranscoder<?> transcoder;
            if (transcoderClasses.length == 0) {
                transcoder = null;
            } else if (transcoderClasses.length > 1) {
                throw new ProxyAnnotationException("Multiple transcoders defined on method " + methodName());
            } else {
                transcoder = Utils.newInstance(transcoderClasses[0]);
            }

            evCacheOptions = new EvCacheOptions(
                    annotation.appName(),
                    annotation.name(),
                    annotation.enableZoneFallback(),
                    annotation.ttl(),
                    transcoder,
                    annotation.cacheKeyTemplate());
        }

        private String methodName() {
            return method.getDeclaringClass().getSimpleName() + '.' + method.getName();
        }
    }
}
