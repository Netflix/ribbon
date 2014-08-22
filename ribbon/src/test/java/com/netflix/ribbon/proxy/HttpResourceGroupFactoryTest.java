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
import com.netflix.ribbon.RibbonTransportFactory;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieService;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieServiceWithResourceGroupClassAnnotation;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieServiceWithResourceGroupNameAnnotation;
import com.netflix.ribbon.proxy.sample.ResourceGroupClasses.SampleHttpResourceGroup;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class HttpResourceGroupFactoryTest {

    @Test
    public void testResourceGroupAnnotationMissing() throws Exception {
        ClassTemplate<SampleMovieService> classTemplate = new ClassTemplate<SampleMovieService>(SampleMovieService.class);
        new ProxyHttpResourceGroupFactory<SampleMovieService>(classTemplate, new DefaultResourceFactory(ClientConfigFactory.DEFAULT, RibbonTransportFactory.DEFAULT, AnnotationProcessorsProvider.DEFAULT),
                AnnotationProcessorsProvider.DEFAULT).createResourceGroup();
    }

    @Test
    public void testCreateWithResourceGroupNameAnnotation() throws Exception {
        testResourceGroupCreation(SampleMovieServiceWithResourceGroupNameAnnotation.class, HttpResourceGroup.class);
    }

    @Test
    public void testCreateWithResourceGroupClassAnnotation() throws Exception {
        testResourceGroupCreation(SampleMovieServiceWithResourceGroupClassAnnotation.class, SampleHttpResourceGroup.class);
    }

    private void testResourceGroupCreation(Class<?> clientInterface, Class<? extends HttpResourceGroup> httpResourceGroupClass) {
        ClassTemplate classTemplate = new ClassTemplate(clientInterface);
        HttpResourceGroup resourceGroup = new ProxyHttpResourceGroupFactory(classTemplate).createResourceGroup();
        assertNotNull("got null and expected instance of " + httpResourceGroupClass, resourceGroup);
    }
}
