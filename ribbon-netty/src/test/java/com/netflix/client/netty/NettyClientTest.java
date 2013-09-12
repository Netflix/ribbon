package com.netflix.client.netty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.netflix.client.ClientException;
import com.netflix.client.ResponseCallback;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.netty.http.AsyncNettyHttpClient;
import com.netflix.client.netty.http.LoadBalancingNettyClient;
import com.netflix.client.netty.http.NettyHttpRequest;
import com.netflix.client.netty.http.NettyHttpRequest.Verb;
import com.netflix.client.netty.http.NettyHttpResponse;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.Server;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.net.httpserver.HttpServer;

public class NettyClientTest {
    
    private static HttpServer server = null;
    private static String SERVICE_URI;

    private volatile Person person;
    
    private AsyncNettyHttpClient client = new AsyncNettyHttpClient(new DefaultClientConfigImpl());
    private static int port;

    @BeforeClass 
    public static void init() throws Exception {
        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.client.netty");
        port = (new Random()).nextInt(1000) + 4000;
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
        Thread.sleep(2000);
        assertEquals(EmbeddedResources.defaultPerson, person);
    }

    @Test
    public void testPost() throws Exception {
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
        Thread.sleep(2000);
        assertEquals(myPerson, person);
    }

    @Test
    public void testQuery() throws Exception {
        URI uri = new URI(SERVICE_URI + "testNetty/personQuery");
        Person myPerson = new Person("hello world", 4);
        Multimap<String, String> queryParams = ArrayListMultimap.<String, String>create();
        queryParams.put("age", String.valueOf(myPerson.age));
        queryParams.put("name", String.valueOf(myPerson.name));
        NettyHttpRequest request = NettyHttpRequest.newBuilder().setUri(uri).setQueryParams(queryParams.asMap()).build();
        
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
        Thread.sleep(2000);
        assertEquals(myPerson, person);        
    }
    
    @Test
    public void testReadTimeout() throws Exception {
        URI uri = new URI(SERVICE_URI + "testNetty/readTimeout");
        NettyHttpRequest request = NettyHttpRequest.newBuilder().setUri(uri).build();
        person = null;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        client.execute(request, new ResponseCallback<NettyHttpResponse>() {            
            @Override
            public void onResponseReceived(NettyHttpResponse response) {
                try {
                    person = response.get(Person.class);
                    System.out.println(person);
                } catch (ClientException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onException(Throwable e) {
                e.printStackTrace();
                exception.set(e);
            }
        });
        Thread.sleep(5000);
        assertNull(person);
        assertNotNull(exception.get());
    }

    @Test
    public void testLoadBalancingClient() throws Exception {
        LoadBalancingNettyClient loadBalancingClient = new LoadBalancingNettyClient();
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        List<String> servers = Lists.newArrayList("localhost:" + port);
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/testNetty/person");
        person = null;
        NettyHttpRequest request = NettyHttpRequest.newBuilder().setUri(uri).build();
        loadBalancingClient.execute(request, new ResponseCallback<NettyHttpResponse>() {            
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
        Thread.sleep(2000);
        assertEquals(EmbeddedResources.defaultPerson, person);
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(new Server("localhost:" + port)).getTotalRequestsCount());        
    }
    
    @Test
    public void testLoadBalancingClientWithRetry() throws Exception {
        LoadBalancingNettyClient loadBalancingClient = new LoadBalancingNettyClient();
        loadBalancingClient.setMaxAutoRetries(1);
        loadBalancingClient.setMaxAutoRetriesNextServer(1);
        List<String> servers = Lists.newArrayList("localhost:" + port);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        person = null;
        URI uri = new URI("/testNetty/readTimeout");
        NettyHttpRequest request = NettyHttpRequest.newBuilder().setUri(uri).build();
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        loadBalancingClient.execute(request, new ResponseCallback<NettyHttpResponse>() {            
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
                exception.set(e);
                e.printStackTrace();
            }
        });
        Thread.sleep(10000);
        assertNull(person);
        assertNotNull(exception.get());
        // assertTrue(exception.get().getCause() instanceof io.netty.handler.timeout.ReadTimeoutException);
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(new Server("localhost:" + port)).getTotalRequestsCount());                
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(new Server("localhost:" + port)).getSuccessiveConnectionFailureCount());                
    }
}
