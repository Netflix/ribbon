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
package com.netflix.ribbon.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.netflix.client.config.ClientConfigFactory;
import com.netflix.ribbon.DefaultResourceFactory;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.RibbonTransportFactory;
import com.netflix.ribbon.RibbonTransportFactory.DefaultRibbonTransportFactory;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider.DefaultAnnotationProcessorsProvider;

/**
 * Default bindings for Ribbon
 * 
 * @author elandau
 *
 */
public class RibbonModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ClientConfigFactory.class).toInstance(ClientConfigFactory.DEFAULT);
        bind(RibbonTransportFactory.class).to(DefaultRibbonTransportFactory.class).in(Scopes.SINGLETON);
        bind(AnnotationProcessorsProvider.class).to(DefaultAnnotationProcessorsProvider.class).in(Scopes.SINGLETON);
        bind(RibbonResourceFactory.class).to(DefaultResourceFactory.class).in(Scopes.SINGLETON);
    }
}
