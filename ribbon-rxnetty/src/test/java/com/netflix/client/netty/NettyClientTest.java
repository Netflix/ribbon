package com.netflix.client.netty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.BeforeClass;
import org.junit.Test;

import rx.Observable;
import rx.util.functions.Action1;
import rx.util.functions.Func1;

import com.google.common.collect.Lists;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.client.ClientException;
import com.netflix.client.LoadBalancerObservables;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.http.UnexpectedHttpResponseException;
import com.netflix.client.netty.http.NettyHttpLoadBalancerErrorHandler;
import com.netflix.client.netty.http.RxNettyHttpClient;
import com.netflix.client.netty.http.RxNettyHttpLoadBalancingClient;
import com.netflix.client.netty.http.ServerSentEvent;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.ribbon.test.resources.EmbeddedResources;
import com.netflix.ribbon.test.resources.EmbeddedResources.Person;
import com.netflix.serialization.JacksonCodec;
import com.netflix.serialization.StringDeserializer;
import com.netflix.serialization.TypeDef;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.net.httpserver.HttpServer;

public class NettyClientTest {
    
    private static HttpServer server = null;
    private static String SERVICE_URI;
    private static int port;

    
    @BeforeClass 
    public static void init() throws Exception {
        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.ribbon.test.resources");
        port = (new Random()).nextInt(1000) + 4000;
        SERVICE_URI = "http://localhost:" + port + "/";
        ExecutorService service = Executors.newFixedThreadPool(20);
        try{
            server = HttpServerFactory.create(SERVICE_URI, resourceConfig);           
            server.setExecutor(service);
            server.start();
        } catch(Exception e) {
            e.printStackTrace();
            fail("Unable to start server");
        }
        // LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
    }
    
    @Test
    public void testObservable() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient();
        final List<Person> result = Lists.newArrayList();
        observableClient.createEntityObservable(request, TypeDef.fromClass(Person.class)).toBlockingObservable().forEach(new Action1<Person>() {
            @Override
            public void call(Person t1) {
                try {
                    result.add(t1);
                } catch (Exception e) { 
                    e.printStackTrace();
                }
            }
        });
        assertEquals(Lists.newArrayList(EmbeddedResources.defaultPerson), result);
    }

    @Test
    public void testRedirect() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/redirect");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).queryParam("port", String.valueOf(port)).build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues().setPropertyWithType(CommonClientConfigKey.ReadTimeout, 1000000));
        final List<Person> result = Lists.newArrayList();
        observableClient.createEntityObservable(request, TypeDef.fromClass(Person.class)).subscribe(new Action1<Person>() {
            @Override
            public void call(Person t1) {
                try {
                    result.add(t1);
                } catch (Exception e) { 
                    e.printStackTrace();
                }
            }
        }, new Action1<Throwable>() {

            @Override
            public void call(Throwable t1) {
                t1.printStackTrace();
            }
            
        }
        );
        Thread.sleep(2000);
        assertEquals(Lists.newArrayList(EmbeddedResources.defaultPerson), result);
    }

    @Test
    public void testWithOverrideDeserializer() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        DefaultClientConfigImpl overrideConfig = new DefaultClientConfigImpl();
        overrideConfig.setPropertyWithType(CommonClientConfigKey.Deserializer, StringDeserializer.getInstance());
        HttpRequest request = HttpRequest.newBuilder().uri(uri).overrideConfig(overrideConfig).build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient();
        final List<String> result = Lists.newArrayList();
        observableClient.createEntityObservable(request, TypeDef.fromClass(String.class)).toBlockingObservable().forEach(new Action1<String>() {
            @Override
            public void call(String t1) {
                try {
                    result.add(t1);
                } catch (Exception e) { 
                    e.printStackTrace();
                }
            }
        });
        List<String> expected = Lists.newArrayList("{\"name\":\"ribbon\",\"age\":1}");
        assertEquals(expected, result);
    }

    
    @Test
    public void testMultipleObsers() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient();
        Observable<Person> observable = observableClient.createEntityObservable(request, TypeDef.fromClass(Person.class)).cache();
        final IdentityHashMap<Person, String> map = new IdentityHashMap<EmbeddedResources.Person, String>();
        final CountDownLatch latch = new CountDownLatch(3);
        Action1<Person> onNext = new Action1<Person>() {
            @Override
            public void call(Person t1) {
                map.put(t1, "");
                latch.countDown();
            }
        };

        for (int i = 0; i < 3; i++) {
            observable.subscribe(onNext);
        }
        if (!latch.await(2000, TimeUnit.MILLISECONDS)) {
            fail("Observer is not called within time out");
        }
        assertEquals(1, map.size());
    }

    @Test
    public void testFullResponse() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient();
        final List<Person> result = Lists.newArrayList();
        final CountDownLatch latch = new CountDownLatch(1);        
        observableClient.createEntityObservable(request, TypeDef.fromClass(HttpResponse.class)).subscribe(new Action1<HttpResponse>() {
            @Override
            public void call(HttpResponse t1) {
                try {
                    result.add(t1.getEntity(Person.class));
                    latch.countDown();
                } catch (Exception e) { 
                    e.printStackTrace();
                    latch.countDown();
                }
            }
        });
        latch.await();
        assertEquals(Lists.newArrayList(EmbeddedResources.defaultPerson), result);
    }
    
    @Test
    public void testPostWithObservable() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        Person myPerson = new Person("netty", 5);
        HttpRequest request = HttpRequest.newBuilder().uri(uri).verb(Verb.POST).entity(myPerson).header("Content-type", "application/json").build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient();
        final List<Person> result = Lists.newArrayList();
        observableClient.createEntityObservable(request, TypeDef.fromClass(Person.class)).toBlockingObservable().forEach(new Action1<Person>() {
            @Override
            public void call(Person t1) {
                try {
                    result.add(t1);
                } catch (Exception e) { // NOPMD
                }
            }
        });
        assertEquals(1, result.size());
        assertEquals(myPerson, result.get(0));
    }

    @Test
    public void testConnectTimeoutObservable() throws Exception {
        RxNettyHttpClient observableClient = new RxNettyHttpClient(
                DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1"));
        HttpRequest request = HttpRequest.newBuilder().uri("http://www.google.com:81/").build();
        Observable<HttpResponse> observable = observableClient.createEntityObservable(request, TypeDef.fromClass(HttpResponse.class));
        
        ObserverWithLatch<HttpResponse> observer = new ObserverWithLatch<HttpResponse>();
        observable.subscribe(observer);
        observer.await();
        assertTrue(observer.error instanceof io.netty.channel.ConnectTimeoutException);
    }
    
    @Test
    public void testReadTimeout() throws Exception {
        RxNettyHttpClient observableClient = new RxNettyHttpClient(
                DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ReadTimeout, "100"));
        URI uri = new URI(SERVICE_URI + "testAsync/readTimeout");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        Observable<HttpResponse> observable = observableClient.createEntityObservable(request, TypeDef.fromClass(HttpResponse.class));
        ObserverWithLatch<HttpResponse> observer = new ObserverWithLatch<HttpResponse>();
        observable.subscribe(observer);
        observer.await();
        assertTrue(observer.error instanceof io.netty.handler.timeout.ReadTimeoutException);      
        Observable<Person> person = observableClient.createEntityObservable(request, TypeDef.fromClass(Person.class));
        ObserverWithLatch<Person> personObserver = new ObserverWithLatch<Person>();
        person.subscribe(personObserver);
        personObserver.await();
        assertTrue(personObserver.error instanceof io.netty.handler.timeout.ReadTimeoutException);      
    }
    
    
    @Test
    public void testSameServerObservable() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1");
        final RxNettyHttpClient observableClient = new RxNettyHttpClient(config);
        HttpRequest request = HttpRequest.newBuilder().uri("http://www.google.com:81/").build();
        LoadBalancerObservables<HttpRequest, HttpResponse> lbObservables = new LoadBalancerObservables<HttpRequest, HttpResponse>(config);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(2);
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
        Observable<HttpResponse> observableWithRetries = lbObservables.retrySameServer(request, observableClient.createEntityObservable(request, TypeDef.fromClass(HttpResponse.class)));
        ObserverWithLatch<HttpResponse> observer = new ObserverWithLatch<HttpResponse>();
        observableWithRetries.subscribe(observer);
        observer.await();
        ServerStats stats = lbObservables.getServerStats(new Server("www.google.com:81"));
        assertEquals(3, stats.getTotalRequestsCount());
        assertEquals(0, stats.getActiveRequestsCount());
        assertEquals(3, stats.getSuccessiveConnectionFailureCount());
    }
    
    @Test
    public void testSameServerObservableWithSuccess() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
        final RxNettyHttpClient observableClient = new RxNettyHttpClient(config);
        HttpRequest request = HttpRequest.newBuilder().uri("http://www.google.com:80/").build();
        LoadBalancerObservables<HttpRequest, HttpResponse> lbObservables = new LoadBalancerObservables<HttpRequest, HttpResponse>(config);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(1);
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
        Observable<HttpResponse> observableWithRetries = lbObservables.retrySameServer(request, observableClient.createEntityObservable(request, TypeDef.fromClass(HttpResponse.class)));
        ObserverWithLatch<HttpResponse> observer = new ObserverWithLatch<HttpResponse>();
        observableWithRetries.subscribe(observer);
        observer.await();
        assertEquals(200, observer.obj.getStatus());
        ServerStats stats = lbObservables.getServerStats(new Server("www.google.com:80"));
        assertEquals(1, stats.getTotalRequestsCount());
        assertEquals(0, stats.getActiveRequestsCount());
        assertEquals(0, stats.getSuccessiveConnectionFailureCount());
    }
    
    @Test
    public void testObservableWithMultipleServers() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1000");
        URI uri = new URI("/testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        
        RxNettyHttpLoadBalancingClient lbObservables = new RxNettyHttpLoadBalancingClient(config);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        Server badServer = new Server("localhost:12345");
        Server goodServer = new Server("localhost:" + port);
        List<Server> servers = Lists.newArrayList(badServer, badServer, badServer, goodServer);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(1);
        lbObservables.setMaxAutoRetriesNextServer(3);
        Observable<Person> observableWithRetries = lbObservables.createEntityObservable(request, TypeDef.fromClass(Person.class));
        ObserverWithLatch<Person> observer = new ObserverWithLatch<Person>();
        observableWithRetries.subscribe(observer);
        observer.await();
        assertEquals(EmbeddedResources.defaultPerson, observer.obj);
        ServerStats stats = lbObservables.getServerStats(badServer);
        // two requests to bad server because retry same server is set to 1
        assertEquals(4, stats.getTotalRequestsCount());
        assertEquals(0, stats.getActiveRequestsCount());
        assertEquals(4, stats.getSuccessiveConnectionFailureCount());
        
        stats = lbObservables.getServerStats(goodServer);
        // two requests to bad server because retry same server is set to 1
        assertEquals(1, stats.getTotalRequestsCount());
        assertEquals(0, stats.getActiveRequestsCount());
        assertEquals(0, stats.getSuccessiveConnectionFailureCount());
    }
    
    @Test
    public void testHttpResponseObservableWithMultipleServers() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1000");
        URI uri = new URI("/testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        
        RxNettyHttpLoadBalancingClient lbObservables = new RxNettyHttpLoadBalancingClient(config);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        Server badServer = new Server("localhost:12345");
        Server goodServer = new Server("localhost:" + port);
        List<Server> servers = Lists.newArrayList(badServer, badServer, badServer, goodServer);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(1);
        lbObservables.setMaxAutoRetriesNextServer(3);
        Observable<HttpResponse> observableWithRetries = lbObservables.createFullHttpResponseObservable(request);
        ObserverWithLatch<HttpResponse> observer = new ObserverWithLatch<HttpResponse>();
        observableWithRetries.subscribe(observer);
        observer.await();
        assertEquals(200, observer.obj.getStatus());
    }

    
    @Test
    public void testLoadBalancingObservablesWithReadTimeout() throws Exception {
        MockWebServer server = new MockWebServer();
        String content = "{\"name\": \"ribbon\", \"age\": 2}";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "application/json")
                .setBody(content));       
        server.play();

        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues()
                .setPropertyWithType(CommonClientConfigKey.ReadTimeout, 100);
        URI uri = new URI("/testAsync/readTimeout");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        
        RxNettyHttpLoadBalancingClient lbObservables = new RxNettyHttpLoadBalancingClient(config);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        Server goodServer = new Server("localhost:" + server.getPort());
        Server badServer = new Server("localhost:" + port);
        List<Server> servers = Lists.newArrayList(goodServer, badServer, badServer, goodServer);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(1);
        lbObservables.setMaxAutoRetriesNextServer(3);
        Observable<Person> observableWithRetries = lbObservables.createEntityObservable(request, TypeDef.fromClass(Person.class));
        ObserverWithLatch<Person> observer = new ObserverWithLatch<Person>();
        observableWithRetries.subscribe(observer);
        observer.await();
        assertEquals("ribbon", observer.obj.name);
        assertEquals(2, observer.obj.age);
        ServerStats stats = lbObservables.getServerStats(badServer);
        server.shutdown();
        // two requests to bad server because retry same server is set to 1
        assertEquals(4, stats.getTotalRequestsCount());
        assertEquals(0, stats.getActiveRequestsCount());
        assertEquals(4, stats.getSuccessiveConnectionFailureCount());
        
        stats = lbObservables.getServerStats(goodServer);
        // two requests to bad server because retry same server is set to 1
        assertEquals(1, stats.getTotalRequestsCount());
        assertEquals(0, stats.getActiveRequestsCount());
        assertEquals(0, stats.getSuccessiveConnectionFailureCount());
    }

    
    @Test
    public void testObservableWithMultipleServersFailed() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1000");
        URI uri = new URI("/testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        
        RxNettyHttpLoadBalancingClient lbObservables = new RxNettyHttpLoadBalancingClient(config);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        Server badServer = new Server("localhost:12345");
        Server badServer1 = new Server("localhost:12346");
        Server badServer2 = new Server("localhost:12347");

        List<Server> servers = Lists.newArrayList(badServer, badServer1, badServer2);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(1);
        lbObservables.setMaxAutoRetriesNextServer(3);
        Observable<Person> observableWithRetries = lbObservables.createEntityObservable(request, TypeDef.fromClass(Person.class));
        ObserverWithLatch<Person> observer = new ObserverWithLatch<Person>();
        observableWithRetries.subscribe(observer);
        observer.await();
        assertNull(observer.obj);
        assertTrue(observer.error instanceof ClientException);
        ServerStats stats = lbObservables.getServerStats(badServer);
        // two requests to bad server because retry same server is set to 1
        assertEquals(2, stats.getTotalRequestsCount());
        assertEquals(0, stats.getActiveRequestsCount());
        assertEquals(2, stats.getSuccessiveConnectionFailureCount());
    }
    
    @Test
    public void testStream() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/personStream")
                .overrideConfig(new DefaultClientConfigImpl().setPropertyWithType(CommonClientConfigKey.Deserializer, JacksonCodec.getInstance()))
                .build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient();
        final List<Person> result = Lists.newArrayList();
        observableClient.createServerSentEventEntityObservable(request, TypeDef.fromClass(Person.class)).toBlockingObservable().forEach(new Action1<ServerSentEvent<Person>>() {
            @Override
            public void call(ServerSentEvent<Person> t1) {
                result.add(t1.getEntity());
            }
        });
        assertEquals(EmbeddedResources.entityStream, result);
    }
    
    @Test
    public void testStreamWithLoadBalancer() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1000");
        RxNettyHttpLoadBalancingClient lbObservables = new RxNettyHttpLoadBalancingClient(config);
        HttpRequest request = HttpRequest.newBuilder().uri("/testAsync/personStream")
                .overrideConfig(new DefaultClientConfigImpl().setPropertyWithType(CommonClientConfigKey.Deserializer, JacksonCodec.getInstance()))
                .build();
        final List<Person> result = Lists.newArrayList();
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        Server goodServer = new Server("localhost:" + port);
        Server badServer = new Server("localhost:12245");
        List<Server> servers = Lists.newArrayList(badServer, badServer, badServer, goodServer);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(1);
        lbObservables.setMaxAutoRetriesNextServer(3);

        lbObservables.createServerSentEventEntityObservable(request, TypeDef.fromClass(Person.class)).toBlockingObservable().forEach(new Action1<ServerSentEvent<Person>>() {
            @Override
            public void call(ServerSentEvent<Person> t1) {
                result.add(t1.getEntity());
            }
        });
        assertEquals(EmbeddedResources.entityStream, result);
    }

    
    @Test
    public void testNoEntity() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/noEntity");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient();
        Observable<HttpResponse> responseObservable = observableClient.createFullHttpResponseObservable(request);
        final AtomicBoolean hasEntity = new AtomicBoolean(true);
        responseObservable.toBlockingObservable().forEach(new Action1<HttpResponse>() {
            @Override
            public void call(HttpResponse t1) {
                hasEntity.set(t1.hasEntity());
            }
        });
        assertFalse(hasEntity.get());
        Observable<Person> personObservable = observableClient.createEntityObservable(request, TypeDef.fromClass(Person.class));
        personObservable.toBlockingObservable().forEach(new Action1<Person>() {
            @Override
            public void call(Person t1) {
                hasEntity.set(true);
            }
        });
        assertFalse(hasEntity.get());        
    }


    @Test
    public void testQuery() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/personQuery");
        Person myPerson = new Person("hello world", 4);
        HttpRequest request = HttpRequest.newBuilder().uri(uri).queryParams("age", String.valueOf(myPerson.age))
                .queryParams("name", myPerson.name).build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient();
        final List<Person> result = Lists.newArrayList();
        observableClient.createEntityObservable(request, TypeDef.fromClass(Person.class)).toBlockingObservable().forEach(new Action1<Person>() {
            @Override
            public void call(Person t1) {
                try {
                    result.add(t1);
                } catch (Exception e) { // NOPMD
                }
            }
        });
        assertEquals(1, result.size());
        assertEquals(myPerson, result.get(0));
    }
    
    @Test
    public void testThrottle() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/throttle");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        RxNettyHttpClient client = new RxNettyHttpClient();
        final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
        Person p = client.createEntityObservable(request, TypeDef.fromClass(Person.class)).onErrorReturn(new Func1<Throwable, Person>() {
            @Override
            public Person call(Throwable t1) {
                throwable.set(t1);
                return null;
            }
            
        }).toBlockingObservable().last();
        assertNull(p);
        assertTrue(throwable.get() instanceof UnexpectedHttpResponseException);
        assertEquals("Service Unavailable", throwable.get().getMessage());
        UnexpectedHttpResponseException ex =  (UnexpectedHttpResponseException) throwable.get();
        assertEquals(503, ex.getStatusCode());
        String body = ex.getResponse().getEntity(String.class);
        assertEquals("Rate exceeds limit", body);
    }

    @Test
    public void testUnexpectedResponse() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/throttle");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        RxNettyHttpClient client = new RxNettyHttpClient();
        Observable<HttpResponse> responseObservable = client.createFullHttpResponseObservable(request);
        final AtomicReference<HttpResponse> response = new AtomicReference<HttpResponse>();
        responseObservable.toBlockingObservable().forEach(new Action1<HttpResponse>() {

            @Override
            public void call(HttpResponse t1) {
                response.set(t1);
            }
            
        });
        assertEquals(503, response.get().getStatus());
        String body = response.get().getEntity(String.class);
        assertEquals("Rate exceeds limit", body);
    }
}
