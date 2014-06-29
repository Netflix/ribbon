package com.netflix.ribbonclientextensions.proxy;

import com.netflix.ribbonclientextensions.CacheProvider;
import com.netflix.ribbonclientextensions.evache.EvCacheOptions;
import com.netflix.ribbonclientextensions.proxy.sample.EvCacheClasses.SampleEVCacheTranscoder;
import com.netflix.ribbonclientextensions.proxy.sample.Movie;
import com.netflix.ribbonclientextensions.proxy.sample.MovieServiceInterfaces.BrokenMovieService;
import com.netflix.ribbonclientextensions.proxy.sample.MovieServiceInterfaces.HystrixOptionalAnnotationValues;
import com.netflix.ribbonclientextensions.proxy.sample.MovieServiceInterfaces.SampleMovieService;
import com.netflix.ribbonclientextensions.proxy.sample.SampleCacheProviderFactory.SampleCacheProvider;
import org.junit.Test;

import static com.netflix.ribbonclientextensions.proxy.Utils.*;
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
        assertEquals("/movies/{id}", template.getPath());

        assertTrue("value1".equals(template.getHeaders().get("X-MyHeader1")));
        assertTrue("value2".equals(template.getHeaders().get("X-MyHeader2")));

        assertEquals(0, template.getParamPosition(0));
        assertEquals(template.getResultType(), Movie.class);

        assertEquals("findMovieById/{id}", template.getHystrixCacheKey());
        assertNotNull(template.getHystrixFallbackHandler());
        assertNotNull(template.getHystrixResponseValidator());

        CacheProvider cacheProvider = template.getCacheProviders().get("findMovieById_{id}");
        assertNotNull(cacheProvider);
        assertTrue(cacheProvider instanceof SampleCacheProvider);

        EvCacheOptions evOpts = template.getEvCacheOptions();
        assertNotNull(evOpts);
        assertEquals("movie-cache", evOpts.getCacheName());
        assertEquals("movieService", evOpts.getAppName());
        assertEquals("movie-{id}", evOpts.getCacheKeyTemplate());
        assertEquals(50, evOpts.getTimeToLive());
        assertTrue(evOpts.getTranscoder() instanceof SampleEVCacheTranscoder);
    }

    @Test
    public void testGetWithTwoParameters() throws Exception {
        MethodTemplate template = new MethodTemplate(methodByName(SampleMovieService.class, "findMovie"));

        assertEquals("findMovie", template.getTemplateName());
        assertEquals("/movies?name={name}&author={author}", template.getPath());
        assertEquals("name", template.getParamName(0));
        assertEquals(0, template.getParamPosition(0));
        assertEquals("author", template.getParamName(1));
        assertEquals(1, template.getParamPosition(1));
    }

    @Test
    public void testHystrixOptionalParameters() throws Exception {
        MethodTemplate template = new MethodTemplate(methodByName(HystrixOptionalAnnotationValues.class, "hystrixWithCacheKeyOnly"));
        assertNotNull(template.getHystrixCacheKey());
        assertNull(template.getHystrixResponseValidator());
        assertNull(template.getHystrixFallbackHandler());

        template = new MethodTemplate(methodByName(HystrixOptionalAnnotationValues.class, "hystrixWithValidatorOnly"));
        assertNull(template.getHystrixCacheKey());
        assertNotNull(template.getHystrixResponseValidator());
        assertNull(template.getHystrixFallbackHandler());

        template = new MethodTemplate(methodByName(HystrixOptionalAnnotationValues.class, "hystrixWithFallbackHandlerOnly"));
        assertNull(template.getHystrixCacheKey());
        assertNull(template.getHystrixResponseValidator());
        assertNotNull(template.getHystrixFallbackHandler());
    }

    @Test
    public void testFromFactory() throws Exception {
        MethodTemplate[] methodTemplates = MethodTemplate.from(SampleMovieService.class);
        assertEquals(SampleMovieService.class.getMethods().length, methodTemplates.length);
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