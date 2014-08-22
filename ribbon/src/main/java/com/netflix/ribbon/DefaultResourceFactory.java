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
package com.netflix.ribbon;

import com.netflix.client.config.ClientConfigFactory;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider;

import javax.inject.Inject;

public class DefaultResourceFactory extends RibbonResourceFactory {

    @Inject
    public DefaultResourceFactory(ClientConfigFactory clientConfigFactory, RibbonTransportFactory transportFactory,
                                  AnnotationProcessorsProvider annotationProcessorsProvider) {
        super(clientConfigFactory, transportFactory, annotationProcessorsProvider);
    }

    public DefaultResourceFactory(ClientConfigFactory clientConfigFactory, RibbonTransportFactory transportFactory) {
        super(clientConfigFactory, transportFactory, AnnotationProcessorsProvider.DEFAULT);
    }
}
