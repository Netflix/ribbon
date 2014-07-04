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

import org.junit.Test;

import com.netflix.ribbon.proxy.ClassTemplate;
import com.netflix.ribbon.proxy.RibbonProxyException;

import static com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class ClassTemplateTest {

    @Test
    public void testResourceGroupAnnotationMissing() throws Exception {
        ClassTemplate classTemplate = new ClassTemplate(SampleMovieService.class);
        assertNull("resource group class not expected", classTemplate.getResourceGroupClass());
        assertNull("resource name not expected", classTemplate.getResourceGroupName());
    }

    @Test
    public void testCreateWithResourceGroupNameAnnotation() throws Exception {
        ClassTemplate classTemplate = new ClassTemplate(SampleMovieServiceWithResourceGroupNameAnnotation.class);
        assertNull("resource group class not expected", classTemplate.getResourceGroupClass());
        assertNotNull("resource name expected", classTemplate.getResourceGroupName());
    }

    @Test
    public void testCreateWithResourceGroupClassAnnotation() throws Exception {
        ClassTemplate classTemplate = new ClassTemplate(SampleMovieServiceWithResourceGroupClassAnnotation.class);
        assertNotNull("resource group class expected", classTemplate.getResourceGroupClass());
        assertNull("resource name not expected", classTemplate.getResourceGroupName());
    }

    @Test(expected = RibbonProxyException.class)
    public void testBothNameAndResourceGroupClassInAnnotation() throws Exception {
        new ClassTemplate(BrokenMovieServiceWithResourceGroupNameAndClassAnnotation.class);
    }
}