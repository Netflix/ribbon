package com.netlfix.resource;

import com.google.common.collect.Multimap;
import com.netflix.client.http.HttpRequest;
import com.netflix.ribbonclientextensions.HttpService;
import com.netflix.ribbonclientextensions.Resource;
import com.netflix.ribbonclientextensions.ResourceTemplate;
import com.sun.jersey.client.impl.ClientRequestImpl;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public class TestTemplates {

    @Test
    public void testSimple() {
        HttpService defaults = new HttpService()
                .withClientVersion("clientName");
        ResourceTemplate<String> template = new ResourceTemplate<String>(
                HttpRequest.Verb.GET, String.class,
                "/test/v1/{id1}/{id2}",
                defaults);
        Resource<String> resource = template.resource();

        try {
            URI uri = resource.toURI();
            Assert.fail("should have thrown an exception because there are no values for the variables in the URI");
        } catch (URISyntaxException e) {
            Assert.assertTrue("Wrong error message", e.getReason().contains("was not supplied"));
        }

        resource.withTarget("id1", "val1");
        try {
            URI uri = resource.toURI();
            Assert.fail("should have thrown an exception because one of the variables in the URI is missing");
        } catch (URISyntaxException e) {
            Assert.assertTrue("Wrong error message", e.getReason().contains("was not supplied"));
        }

        resource.withTarget("id2", "val2");
        try {
            URI uri = resource.toURI();
            Assert.assertEquals( "URI does not have expected value", "/test/v1/val1/val2", uri.toString());
        } catch (URISyntaxException e) {
            Assert.fail(e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()));
        }


        //test extra parameter
        resource.withTarget("id3", "val3");
        try {
            URI uri = resource.toURI();
            Assert.assertEquals("URI does not have expected value", "/test/v1/val1/val2", uri.toString());
        } catch (URISyntaxException e) {
            Assert.assertTrue("Wrong error message", e.getReason().contains("extra template variable"));
        }


    }

    @Test
    public void testClientVersion() {
        HttpService defaults = new HttpService()
        .withClientVersion(ClientRequestImpl.class);
        ResourceTemplate <String> template = new ResourceTemplate<String>(
        HttpRequest.Verb.GET, String.class,
                "/test/v1/{id1}/{id2}",
                defaults);
        Resource<String> resource = template.resource();
        Multimap<String, String> headers = resource.getHeaders();
        String value = headers.get(HttpService.X_NETFLIX_CLIENT_IMPLEMENTATION_VERSION).iterator().next();
        Assert.assertEquals("client version mismatch expected the version of the jersey-bundle*.jar", "1.11", value);
    }



    @Test
    public void testMatrix() {
        HttpService defaults = new HttpService()
                .withClientVersion("clientName");
        ResourceTemplate<String> template = new ResourceTemplate<String>(
                HttpRequest.Verb.GET, String.class,
                "/test/v1{;id1}/{id2}",
                defaults);
        Resource<String> resource = template.resource();

        // test no parameters
        try {
            URI uri = resource.toURI();
            Assert.fail("should have thrown an exception because there are no values for the variables in the URI");
        } catch (URISyntaxException e) {
            Assert.assertTrue("Wrong error message", e.getReason().contains("was not supplied"));
        }

        // test that a required parameter is missing
        resource.withTarget("id1", "val1");
        try {
            URI uri = resource.toURI();
            Assert.fail("should have thrown an exception because there is no value for val2");
        } catch (URISyntaxException e) {
            Assert.assertTrue("Wrong error message", e.getReason().contains("was not supplied"));
        }


        // test matrix parameter is missing but required parameters are supplied.
        resource = template.resource();
        resource.withTarget("id2", "val2");
        try {
            URI uri = resource.toURI();
            Assert.assertEquals("URI does not have expected value", "/test/v1/val2", uri.toString());
        } catch (URISyntaxException e) {
            Assert.fail("should Not have thrown an exception because one id1 is a matrix parameter and therefor optional");
        }

        // test required and matrix parameters are supplied.
        resource.withTarget("id1", "val1");
        try {
            URI uri = resource.toURI();
            Assert.assertEquals("URI does not have expected value", "/test/v1;id1=val1/val2", uri.toString());
        } catch (URISyntaxException e) {
            Assert.fail(e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()));
        }

        //test extra parameter
        resource.withTarget("id3", "val3");
        try {
            URI uri = resource.toURI();
            Assert.assertEquals("URI does not have expected value", "/test/v1/val1/val2", uri.toString());
        } catch (URISyntaxException e) {
            Assert.assertTrue("Wrong error message", e.getReason().contains("extra template variable"));
        }

    }

}
