package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.typedclient.sample.MovieServiceInterfaces.BrokenMovieService;
import com.netflix.ribbonclientextensions.typedclient.sample.MovieServiceInterfaces.SampleMovieService;
import org.junit.Test;

import static com.netflix.ribbonclientextensions.typedclient.ReflectUtil.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class MethodTemplateTest {

    @Test
    public void testGetWithOneParameter() throws Exception {
        MethodTemplate template = new MethodTemplate(methodByName(SampleMovieService.class, "findMovieById"));

        assertEquals("findMovieById", template.getTemplateName());
        assertEquals("/movies/{id}", template.getPath());
        assertEquals("id", template.getParamNames(0));
        assertEquals(0, template.getParamPosition(0));
    }

    @Test
    public void testGetWithTwoParameters() throws Exception {
        MethodTemplate template = new MethodTemplate(methodByName(SampleMovieService.class, "findMovie"));

        assertEquals("findMovie", template.getTemplateName());
        assertEquals("/movies?name={name}&author={author}", template.getPath());
        assertEquals("name", template.getParamNames(0));
        assertEquals(0, template.getParamPosition(0));
        assertEquals("author", template.getParamNames(1));
        assertEquals(1, template.getParamPosition(1));
    }

    @Test
    public void testFromFactory() throws Exception {
        MethodTemplate[] methodTemplates = MethodTemplate.from(SampleMovieService.class);
        assertEquals(3, methodTemplates.length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDetectsInvalidResultType() throws Exception {
        new MethodTemplate(methodByName(BrokenMovieService.class, "returnTypeNotRibbonRequest"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingHttpMethod() throws Exception {
        new MethodTemplate(methodByName(BrokenMovieService.class, "missingHttpAnnotation"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMultipleContentParameters() throws Exception {
        new MethodTemplate(methodByName(BrokenMovieService.class, "multipleContentParameters"));
    }
}