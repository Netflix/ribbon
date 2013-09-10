package com.netflix.client.netty;

import static org.junit.Assert.*;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;

import javax.ws.rs.core.MultivaluedMap;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.netflix.client.ClientException;
import com.netflix.client.ResponseCallback;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.netty.http.AsyncNettyHttpClient;
import com.netflix.client.netty.http.NettyHttpRequest;
import com.netflix.client.netty.http.NettyHttpResponse;
import com.netflix.client.netty.http.NettyHttpRequest.Verb;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.sun.net.httpserver.HttpServer;

public class NettyAgentTest {
    
    private static HttpServer server = null;
    private static String SERVICE_URI;

    private volatile Person person;
    
    @BeforeClass 
    public static void init() throws Exception {
        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.client.netty");
        int port = (new Random()).nextInt(1000) + 4000;
        SERVICE_URI = "http://localhost:" + port + "/";
        try{
            server = HttpServerFactory.create(SERVICE_URI, resourceConfig);           
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testGet() throws Exception {
        AsyncNettyHttpClient client = new AsyncNettyHttpClient(new DefaultClientConfigImpl());
        URI uri = new URI(SERVICE_URI + "testNetty/person");
        NettyHttpRequest request = NettyHttpRequest.newBuilder().setUri(uri).build();
        client.execute(request, new ResponseCallback<NettyHttpResponse>() {            
            @Override
            public void onResponseReceived(NettyHttpResponse response) {
                try {
                    person = response.get(Person.class);
                } catch (ClientException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onException(Throwable e) {
            }
        });
        Thread.sleep(5000);
        assertEquals(EmbeddedResources.defaultPerson, person);
    }

    @Test
    public void testPost() throws Exception {
        AsyncNettyHttpClient client = new AsyncNettyHttpClient(new DefaultClientConfigImpl());
        URI uri = new URI(SERVICE_URI + "testNetty/person");
        Person myPerson = new Person("netty", 5);
        Multimap<String, String> headers = ArrayListMultimap.<String, String>create();
        headers.put("Content-type", "application/json");
        NettyHttpRequest request = NettyHttpRequest.newBuilder().setUri(uri).setVerb(Verb.POST).setEntity(myPerson).setHeaders(headers.asMap()).build();
        
        client.execute(request, new ResponseCallback<NettyHttpResponse>() {            
            @Override
            public void onResponseReceived(NettyHttpResponse response) {
                try {
                    person = response.get(Person.class);
                } catch (ClientException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onException(Throwable e) {
            }
        });
        Thread.sleep(5000);
        assertEquals(myPerson, person);
    }


}
