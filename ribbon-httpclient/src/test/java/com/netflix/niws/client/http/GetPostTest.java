package com.netflix.niws.client.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

import com.netflix.config.ConfigurationManager;
import com.netflix.niws.client.ClientFactory;
import com.netflix.niws.client.http.HttpClientRequest.Verb;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.net.httpserver.HttpServer;

public class GetPostTest {

    private static HttpServer server = null;
    private static String SERVICE_URI;
	private static SimpleJerseyClient client;

    @BeforeClass 
    public static void init() throws Exception {
        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.niws.http", "com.netflix.niws.client");
        int port = (new Random()).nextInt(1000) + 4000;
        SERVICE_URI = "http://localhost:" + port + "/";
        try{
            server = HttpServerFactory.create(SERVICE_URI, resourceConfig);           
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        ConfigurationManager.getConfigInstance().setProperty("GetPostTest.niws.client.ChunkedEncodingSize", "5");
        client = (SimpleJerseyClient) ClientFactory.getNamedClient("GetPostTest");
    }
    
    @AfterClass
    public static void shutDown() {
    	server.stop(0);
    }
    
    @Test
    public void testGet() throws Exception {
    	URI getUri = new URI(SERVICE_URI + "test/getObject");
    	HttpClientRequest request = HttpClientRequest.newBuilder().setUri(getUri).build();
    	HttpClientResponse response = client.execute(request);
    	assertEquals(200, response.getStatus());
    	assertTrue(response.getEntity(TestObject.class).name.equals("test"));
    }

    @Test
    public void testPost() throws Exception {
    	URI getUri = new URI(SERVICE_URI + "test/setObject");
    	TestObject obj = new TestObject();
    	obj.name = "fromClient";
    	HttpClientRequest request = HttpClientRequest.newBuilder().setVerb(Verb.POST).setUri(getUri).setEntity(obj).build();
    	HttpClientResponse response = client.execute(request);
    	assertEquals(200, response.getStatus());
    	assertTrue(response.getEntity(TestObject.class).name.equals("fromClient"));
    }
    
    @Ignore
    public void testChunkedEncoding() throws Exception {
        String obj = "chunked encoded content";
    	URI postUri = new URI(SERVICE_URI + "test/postStream");
    	InputStream input = new ByteArrayInputStream(obj.getBytes());
    	HttpClientRequest request = HttpClientRequest.newBuilder().setVerb(Verb.POST).setUri(postUri).setEntity(input).build();
    	HttpClientResponse response = client.execute(request);
    	assertEquals(200, response.getStatus());
    	assertTrue(TestResource.lastCallChunked);
    	assertTrue(response.getEntity(String.class).equals(obj));
    }
}        
