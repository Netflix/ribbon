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
import com.netflix.ribbon.DefaultResourceFactory;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.RibbonTransportFactory;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.processor.AnnotationProcessor;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider;

/**
 * @author Tomasz Bak
 */
public class ProxyHttpResourceGroupFactory<T> {
    private final ClassTemplate<T> classTemplate;
    private final RibbonResourceFactory httpResourceGroupFactory;
    private final AnnotationProcessorsProvider processors;

    ProxyHttpResourceGroupFactory(ClassTemplate<T> classTemplate) {
        this(classTemplate, new DefaultResourceFactory(ClientConfigFactory.DEFAULT, RibbonTransportFactory.DEFAULT, AnnotationProcessorsProvider.DEFAULT),
                AnnotationProcessorsProvider.DEFAULT);
    }

    ProxyHttpResourceGroupFactory(ClassTemplate<T> classTemplate, RibbonResourceFactory httpResourceGroupFactory,
                                  AnnotationProcessorsProvider processors) {
        this.classTemplate = classTemplate;
        this.httpResourceGroupFactory = httpResourceGroupFactory;
        this.processors = processors;
    }

    public HttpResourceGroup createResourceGroup() {
        Class<? extends HttpResourceGroup> resourceClass = classTemplate.getResourceGroupClass();
        if (resourceClass != null) {
            return Utils.newInstance(resourceClass);
        } else {
            String name = classTemplate.getResourceGroupName();
            if (name == null) {
                name = classTemplate.getClientInterface().getSimpleName();
            }
            HttpResourceGroup.Builder builder = httpResourceGroupFactory.createHttpResourceGroupBuilder(name);
            for (AnnotationProcessor processor: processors.getProcessors()) {
                processor.process(name, builder, httpResourceGroupFactory, classTemplate.getClientInterface());
            }
            return builder.build();
        }
    }
}
