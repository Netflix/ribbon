package com.netflix.httpasyncclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import rx.Observable;
import rx.util.functions.Action1;

import com.google.common.collect.Lists;
import com.netflix.client.AsyncLoadBalancingClient;
import com.netflix.client.ClientException;
import com.netflix.client.ObservableAsyncClient;
import com.netflix.client.ResponseCallback;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.httpasyncclient.RibbonHttpAsyncClient.AsyncResponse;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.net.httpserver.HttpServer;

public class HttpAsyncClienTest {
    private static HttpServer server = null;
    private static String SERVICE_URI;

    private volatile Person person;
    
    private RibbonHttpAsyncClient client = new RibbonHttpAsyncClient();
    private static int port;

    @BeforeClass 
    public static void init() throws Exception {
        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.httpasyncclient");
        port = (new Random()).nextInt(1000) + 4000;
        SERVICE_URI = "http://localhost:" + port + "/";
        try{
            server = HttpServerFactory.create(SERVICE_URI, resourceConfig);           
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        // LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
    }

    @Test
    public void testGet() throws Exception {
        URI uri = new URI(SERVICE_URI + "testNetty/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        final AtomicReference<RibbonHttpAsyncClient.AsyncResponse> res = new AtomicReference<RibbonHttpAsyncClient.AsyncResponse>();
        client.execute(request, new ResponseCallback<RibbonHttpAsyncClient.AsyncResponse>() {            
            @Override
            public void onResponseReceived(RibbonHttpAsyncClient.AsyncResponse response) {
                try {
                    res.set(response);
                    person = response.get(Person.class);
                } catch (ClientException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onException(Throwable e) {
                exception.set(e);
            }
        });
        Thread.sleep(2000);
        assertEquals(EmbeddedResources.defaultPerson, person);
        assertNull(exception.get());
        assertTrue(res.get().getHeaders().get("Content-type").contains("application/json"));
    }
    
    @Test
    public void testObservable() throws Exception {
        URI uri = new URI(SERVICE_URI + "testNetty/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ObservableAsyncClient<HttpRequest, RibbonHttpAsyncClient.AsyncResponse> observableClient = new ObservableAsyncClient<HttpRequest, RibbonHttpAsyncClient.AsyncResponse>(client);
        final List<Person> result = Lists.newArrayList();
        observableClient.execute(request).toBlockingObservable().forEach(new Action1<AsyncResponse>() {
            @Override
            public void call(AsyncResponse t1) {
                try {
                    result.add(t1.get(Person.class));
                } catch (ClientException e) {
                }
            }
        });
        assertEquals(Lists.newArrayList(EmbeddedResources.defaultPerson), result);
    }

    
    @Test
    public void testNoEntity() throws Exception {
        URI uri = new URI(SERVICE_URI + "testNetty/noEntity");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        final AtomicInteger responseCode = new AtomicInteger();
        final AtomicBoolean hasEntity = new AtomicBoolean(true);
        client.execute(request, new ResponseCallback<AsyncResponse>() {            
            @Override
            public void onResponseReceived(AsyncResponse response) {
                responseCode.set(response.getStatus());
                hasEntity.set(response.hasEntity());
            }
            
            @Override
            public void onException(Throwable e) {
                exception.set(e);
            }
        });
        Thread.sleep(2000);
        assertNull(exception.get());
        assertEquals(200, responseCode.get());
        assertFalse(hasEntity.get());
    }


    @Test
    public void testPost() throws Exception {
        URI uri = new URI(SERVICE_URI + "testNetty/person");
        Person myPerson = new Person("netty", 5);
        HttpRequest request = HttpRequest.newBuilder().uri(uri).verb(Verb.POST).entity(myPerson).header("Content-type", "application/json").build();
        
        client.execute(request, new ResponseCallback<AsyncResponse>() {            
            @Override
            public void onResponseReceived(AsyncResponse response) {
                try {
                    person = response.get(Person.class);
                } catch (ClientException e) { // NOPMD
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
        HttpRequest request = HttpRequest.newBuilder().uri(uri).queryParams("age", String.valueOf(myPerson.age))
                .queryParams("name", myPerson.name).build();
        
        client.execute(request, new ResponseCallback<AsyncResponse>() {            
            @Override
            public void onResponseReceived(AsyncResponse response) {
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
    public void testConnectTimeout() throws Exception {
        RibbonHttpAsyncClient timeoutClient = new RibbonHttpAsyncClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1"));
        HttpRequest request = HttpRequest.newBuilder().uri("http://www.google.com:81/").build();
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        timeoutClient.execute(request, new ResponseCallback<AsyncResponse>() {
            @Override
            public void onResponseReceived(AsyncResponse response) {
                System.err.println("Got response");
            }

            @Override
            public void onException(Throwable e) {
                exception.set(e);
            }
            
        });
        Thread.sleep(2000);
        assertNotNull(exception.get());
    }
    
    @Test
    public void testLoadBalancingClient() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, AsyncResponse> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                RibbonHttpAsyncClient.AsyncResponse>(client);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        List<Server> servers = Lists.newArrayList(new Server("localhost:" + port));
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/testNetty/person");
        person = null;
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        loadBalancingClient.execute(request, new ResponseCallback<AsyncResponse>() {            
            @Override
            public void onResponseReceived(AsyncResponse response) {
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
    public void testLoadBalancingClientMultiServers() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, AsyncResponse> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                RibbonHttpAsyncClient.AsyncResponse>(client);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new RoundRobinRule());
        Server good = new Server("localhost:" + port);
        Server bad = new Server("localhost:" + 33333);
        List<Server> servers = Lists.newArrayList(bad, bad, good);
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        loadBalancingClient.setMaxAutoRetriesNextServer(2);
        URI uri = new URI("/testNetty/person");
        person = null;
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        loadBalancingClient.execute(request, new ResponseCallback<AsyncResponse>() {            
            @Override
            public void onResponseReceived(AsyncResponse response) {
                try {
                    person = response.get(Person.class);
                } catch (ClientException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onException(Throwable e) {
                exception.set(e);
            }
        });
        Thread.sleep(10000);
        assertNull(exception.get());
        assertEquals(EmbeddedResources.defaultPerson, person);
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(good).getTotalRequestsCount());
    }
    
    @Test
    public void testLoadBalancingClientWithRetry() throws Exception {
        RibbonHttpAsyncClient timeoutClient = 
                new RibbonHttpAsyncClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1"));
        AsyncLoadBalancingClient<HttpRequest, AsyncResponse> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                RibbonHttpAsyncClient.AsyncResponse>(timeoutClient);
        loadBalancingClient.setMaxAutoRetries(1);
        loadBalancingClient.setMaxAutoRetriesNextServer(1);
        Server server = new Server("www.microsoft.com:81");
        List<Server> servers = Lists.newArrayList(server);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        loadBalancingClient.execute(request, new ResponseCallback<AsyncResponse>() {            
            @Override
            public void onResponseReceived(AsyncResponse response) {
                System.err.println(response.getStatus());
            }
            
            @Override
            public void onException(Throwable e) {
                exception.set(e);
            }
        });
        Thread.sleep(10000);
        assertNotNull(exception.get());
        // assertTrue(exception.get().getCause() instanceof io.netty.handler.timeout.ReadTimeoutException);
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(server).getTotalRequestsCount());                
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(server).getSuccessiveConnectionFailureCount());                
    }


    
}
