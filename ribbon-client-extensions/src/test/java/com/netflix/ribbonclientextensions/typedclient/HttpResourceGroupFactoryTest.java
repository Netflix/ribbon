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

package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.http.HttpResourceGroup;
import com.netflix.ribbonclientextensions.typedclient.sample.MovieServiceInterfaces.SampleMovieService;
import com.netflix.ribbonclientextensions.typedclient.sample.MovieServiceInterfaces.SampleMovieServiceWithResourceGroupClassAnnotation;
import com.netflix.ribbonclientextensions.typedclient.sample.MovieServiceInterfaces.SampleMovieServiceWithResourceGroupNameAnnotation;
import com.netflix.ribbonclientextensions.typedclient.sample.ResourceGroupClasses.SampleHttpResourceGroup;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class HttpResourceGroupFactoryTest {

    @Test(expected = RibbonTypedClientException.class)
    public void testResourceGroupAnnotationMissing() throws Exception {
        ClassTemplate classTemplate = new ClassTemplate(SampleMovieService.class);
        new HttpResourceGroupFactory(classTemplate).createResourceGroup();
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
        HttpResourceGroup resourceGroup = new HttpResourceGroupFactory(classTemplate).createResourceGroup();
        assertNotNull("got null and expected instance of " + httpResourceGroupClass, resourceGroup);
    }
}