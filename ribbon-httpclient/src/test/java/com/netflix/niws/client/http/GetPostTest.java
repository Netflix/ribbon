/*
*
* Copyright 2013 Netflix, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
package com.netflix.niws.client.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.client.ClientFactory;
import com.netflix.niws.client.http.HttpClientRequest.Verb;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.sun.net.httpserver.HttpServer;

public class GetPostTest {

    private static HttpServer server = null;
    private static String SERVICE_URI;
	private static RestClient client;

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
        client = (RestClient) ClientFactory.getNamedClient("GetPostTest");
    }
    
    @AfterClass
    public static void shutDown() {
    	server.stop(0);
    }
    
    @Test
    public void testGet() throws Exception {
    	URI getUri = new URI(SERVICE_URI + "test/getObject");
    	MultivaluedMapImpl params = new MultivaluedMapImpl();
    	params.add("name", "test");
    	HttpClientRequest request = HttpClientRequest.newBuilder().setUri(getUri).setQueryParams(params).build();
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
    
    @Test
    public void testChunkedEncoding() throws Exception {
        String obj = "chunked encoded content";
    	URI postUri = new URI(SERVICE_URI + "test/postStream");
    	InputStream input = new ByteArrayInputStream(obj.getBytes("UTF-8"));
    	HttpClientRequest request = HttpClientRequest.newBuilder().setVerb(Verb.POST).setUri(postUri).setEntity(input).build();
    	HttpClientResponse response = client.execute(request);
    	assertEquals(200, response.getStatus());
    	assertTrue(response.getEntity(String.class).equals(obj));
    }
}        
