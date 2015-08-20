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

import com.netflix.ribbon.proxy.sample.Movie;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.BrokenMovieService;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.PostsWithDifferentContentTypes;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieService;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.TemplateNameDerivedFromMethodName;
import com.netflix.ribbon.proxy.sample.MovieTransformer;
import io.netty.buffer.ByteBuf;
import org.junit.Test;

import static com.netflix.ribbon.proxy.Utils.methodByName;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class MethodTemplateTest {

    @Test
    public void testGetWithOneParameter() throws Exception {
        MethodTemplate template = new MethodTemplate(methodByName(SampleMovieService.class, "findMovieById"));

        assertEquals("id", template.getParamName(0));
        assertEquals("findMovieById", template.getTemplateName());


        assertEquals(0, template.getParamPosition(0));
        assertEquals(template.getResultType(), ByteBuf.class);
    }

    @Test
    public void testGetWithTwoParameters() throws Exception {
        MethodTemplate template = new MethodTemplate(methodByName(SampleMovieService.class, "findMovie"));

        assertEquals("findMovie", template.getTemplateName());
        assertEquals("name", template.getParamName(0));
        assertEquals(0, template.getParamPosition(0));
        assertEquals("author", template.getParamName(1));
        assertEquals(1, template.getParamPosition(1));
    }

    @Test
    public void testTemplateNameCanBeDerivedFromMethodName() throws Exception {
        MethodTemplate template = new MethodTemplate(methodByName(TemplateNameDerivedFromMethodName.class, "myTemplateName"));
        assertEquals("myTemplateName", template.getTemplateName());
    }

    @Test
    public void testWithRawContentSourceContent() throws Exception {
        MethodTemplate methodTemplate = new MethodTemplate(methodByName(PostsWithDifferentContentTypes.class, "postwithRawContentSource"));

        assertEquals(2, methodTemplate.getContentArgPosition());
        assertNotNull(methodTemplate.getContentTransformerClass());
        assertEquals(Movie.class, methodTemplate.getGenericContentType());
    }

    @Test
    public void testWithByteBufContent() throws Exception {
        MethodTemplate methodTemplate = new MethodTemplate(methodByName(PostsWithDifferentContentTypes.class, "postwithByteBufContent"));

        assertEquals(0, methodTemplate.getContentArgPosition());
        assertNull(methodTemplate.getContentTransformerClass());
    }

    @Test
    public void testWithByteArrayContent() throws Exception {
        MethodTemplate methodTemplate = new MethodTemplate(methodByName(PostsWithDifferentContentTypes.class, "postwithByteArrayContent"));

        assertEquals(0, methodTemplate.getContentArgPosition());
        assertNull(methodTemplate.getContentTransformerClass());
    }

    @Test
    public void testWithStringContent() throws Exception {
        MethodTemplate methodTemplate = new MethodTemplate(methodByName(PostsWithDifferentContentTypes.class, "postwithStringContent"));

        assertEquals(0, methodTemplate.getContentArgPosition());
        assertNull(methodTemplate.getContentTransformerClass());
    }

    @Test
    public void testWithUserClassContent() throws Exception {
        MethodTemplate methodTemplate = new MethodTemplate(methodByName(PostsWithDifferentContentTypes.class, "postwithMovieContent"));

        assertEquals(0, methodTemplate.getContentArgPosition());
        assertNotNull(methodTemplate.getContentTransformerClass());
        assertTrue(MovieTransformer.class.equals(methodTemplate.getContentTransformerClass()));
    }

    @Test(expected = ProxyAnnotationException.class)
    public void testWithUserClassContentAndNotDefinedContentTransformer() {
        new MethodTemplate(methodByName(PostsWithDifferentContentTypes.class, "postwithMovieContentBroken"));
    }

    @Test
    public void testFromFactory() throws Exception {
        MethodTemplate[] methodTemplates = MethodTemplate.from(SampleMovieService.class);
        assertEquals(SampleMovieService.class.getMethods().length, methodTemplates.length);
    }

    @Test(expected = ProxyAnnotationException.class)
    public void testDetectsInvalidResultType() throws Exception {
        new MethodTemplate(methodByName(BrokenMovieService.class, "returnTypeNotRibbonRequest"));
    }

    @Test(expected = ProxyAnnotationException.class)
    public void testMissingHttpMethod() throws Exception {
        new MethodTemplate(methodByName(BrokenMovieService.class, "missingHttpAnnotation"));
    }

    @Test(expected = ProxyAnnotationException.class)
    public void testMultipleContentParameters() throws Exception {
        new MethodTemplate(methodByName(BrokenMovieService.class, "multipleContentParameters"));
    }
}