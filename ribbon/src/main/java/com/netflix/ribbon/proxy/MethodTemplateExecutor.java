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
import com.netflix.ribbon.http.HttpRequestBuilder;
import com.netflix.ribbon.http.HttpRequestTemplate.Builder;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.processor.AnnotationProcessor;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.reactivex.netty.channel.ContentTransformer;
import io.reactivex.netty.channel.StringTransformer;
import rx.Observable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
class MethodTemplateExecutor {

    private static final ContentTransformer<ByteBuf> BYTE_BUF_TRANSFORMER = new ContentTransformer<ByteBuf>() {
        @Override
        public ByteBuf call(ByteBuf toTransform, ByteBufAllocator byteBufAllocator) {
            return toTransform;
        }
    };

    private static final ContentTransformer<byte[]> BYTE_ARRAY_TRANSFORMER = new ContentTransformer<byte[]>() {
        @Override
        public ByteBuf call(byte[] toTransform, ByteBufAllocator byteBufAllocator) {
            ByteBuf byteBuf = byteBufAllocator.buffer(toTransform.length);
            byteBuf.writeBytes(toTransform);
            return byteBuf;
        }
    };

    private static final StringTransformer STRING_TRANSFORMER = new StringTransformer();

    private final HttpResourceGroup httpResourceGroup;
    private final MethodTemplate methodTemplate;
    private final Builder<?> httpRequestTemplateBuilder;

    MethodTemplateExecutor(HttpResourceGroup httpResourceGroup, MethodTemplate methodTemplate, AnnotationProcessorsProvider annotations) {
        this.httpResourceGroup = httpResourceGroup;
        this.methodTemplate = methodTemplate;
        httpRequestTemplateBuilder = createHttpRequestTemplateBuilder();
        for (AnnotationProcessor processor: annotations.getProcessors()) {
            processor.process(methodTemplate.getTemplateName(), httpRequestTemplateBuilder, methodTemplate.getMethod());
        }
    }

    @SuppressWarnings("unchecked")
    public <O> RibbonRequest<O> executeFromTemplate(Object[] args) {
        HttpRequestBuilder<?> requestBuilder = httpRequestTemplateBuilder.build().requestBuilder();
        withParameters(requestBuilder, args);
        withContent(requestBuilder, args);

        return (RibbonRequest<O>) requestBuilder.build();
    }

    private Builder<?> createHttpRequestTemplateBuilder() {
        Builder<?> httpRequestTemplateBuilder = createBaseHttpRequestTemplate(httpResourceGroup);
        return httpRequestTemplateBuilder;
    }

    private Builder<?> createBaseHttpRequestTemplate(HttpResourceGroup httpResourceGroup) {
        Builder<?> httpRequestTemplate;
        if (ByteBuf.class.isAssignableFrom(methodTemplate.getResultType())) {
            httpRequestTemplate = httpResourceGroup.newTemplateBuilder(methodTemplate.getTemplateName());
        } else {
            httpRequestTemplate = httpResourceGroup.newTemplateBuilder(methodTemplate.getTemplateName(), methodTemplate.getResultType());

        }
        return httpRequestTemplate;
    }

    private void withParameters(HttpRequestBuilder<?> requestBuilder, Object[] args) {
        int length = methodTemplate.getParamSize();
        for (int i = 0; i < length; i++) {
            String name = methodTemplate.getParamName(i);
            Object value = args[methodTemplate.getParamPosition(i)];
            requestBuilder.withRequestProperty(name, value);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void withContent(HttpRequestBuilder<?> requestBuilder, Object[] args) {
        if (methodTemplate.getContentArgPosition() < 0) {
            return;
        }
        Object contentValue = args[methodTemplate.getContentArgPosition()];
        if (contentValue instanceof Observable) {
            if (ByteBuf.class.isAssignableFrom(methodTemplate.getGenericContentType())) {
                requestBuilder.withContent((Observable<ByteBuf>) contentValue); 
            } else {
                ContentTransformer contentTransformer = Utils.newInstance(methodTemplate.getContentTransformerClass());
                requestBuilder.withRawContentSource((Observable) contentValue, contentTransformer);
            }
        } else if (contentValue instanceof ByteBuf) {
            requestBuilder.withRawContentSource(Observable.just((ByteBuf) contentValue), BYTE_BUF_TRANSFORMER);
        } else if (contentValue instanceof byte[]) {
            requestBuilder.withRawContentSource(Observable.just((byte[]) contentValue), BYTE_ARRAY_TRANSFORMER);
        } else if (contentValue instanceof String) {
            requestBuilder.withRawContentSource(Observable.just((String) contentValue), STRING_TRANSFORMER);
        } else {
            ContentTransformer contentTransformer = Utils.newInstance(methodTemplate.getContentTransformerClass());
            requestBuilder.withRawContentSource(Observable.just(contentValue), contentTransformer);
        }
    }

    public static Map<Method, MethodTemplateExecutor> from(HttpResourceGroup httpResourceGroup, Class<?> clientInterface, AnnotationProcessorsProvider annotations) {
        MethodTemplate[] methodTemplates = MethodTemplate.from(clientInterface);
        Map<Method, MethodTemplateExecutor> tgm = new HashMap<Method, MethodTemplateExecutor>();
        for (MethodTemplate mt : methodTemplates) {
            tgm.put(mt.getMethod(), new MethodTemplateExecutor(httpResourceGroup, mt, annotations));
        }
        return tgm;
    }
}
