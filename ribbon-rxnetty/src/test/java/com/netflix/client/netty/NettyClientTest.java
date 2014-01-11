package com.netflix.client.netty;

import static org.junit.Assert.assertEquals;
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

import org.junit.BeforeClass;
import org.junit.Test;

import rx.Observable;
import rx.util.functions.Action1;

import com.google.common.collect.Lists;
import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.netty.http.LoadBalancerObservables;
import com.netflix.client.netty.http.NettyHttpLoadBalancerErrorHandler;
import com.netflix.client.netty.http.RxNettyHttpClient;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.ribbon.test.resources.EmbeddedResources;
import com.netflix.ribbon.test.resources.EmbeddedResources.Person;
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
        ExecutorService service = Executors.newFixedThreadPool(200);
        try{
            server = HttpServerFactory.create(SERVICE_URI, resourceConfig);           
            server.setExecutor(service);
            server.start();
        } catch(Exception e) {
            e.printStackTrace();
            fail("Unable to start server");
        }
    }
    
    @Test
    public void testObservable() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient();
        final List<Person> result = Lists.newArrayList();
        observableClient.execute(request, TypeDef.fromClass(Person.class)).toBlockingObservable().forEach(new Action1<Person>() {
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
    public void testWithOverrideDeserializer() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        DefaultClientConfigImpl overrideConfig = new DefaultClientConfigImpl();
        overrideConfig.setTypedProperty(CommonClientConfigKey.Deserializer, new StringDeserializer());
        HttpRequest request = HttpRequest.newBuilder().uri(uri).overrideConfig(overrideConfig).build();
        RxNettyHttpClient observableClient = new RxNettyHttpClient();
        final List<String> result = Lists.newArrayList();
        observableClient.execute(request, TypeDef.fromClass(String.class)).toBlockingObservable().forEach(new Action1<String>() {
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
        Observable<Person> observable = observableClient.execute(request, TypeDef.fromClass(Person.class)).cache();
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
        observableClient.execute(request, TypeDef.fromClass(HttpResponse.class)).subscribe(new Action1<HttpResponse>() {
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
        observableClient.execute(request, TypeDef.fromClass(Person.class)).toBlockingObservable().forEach(new Action1<Person>() {
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
        Observable<HttpResponse> observable = observableClient.execute(request, TypeDef.fromClass(HttpResponse.class));
        
        ObserverWithLatch<HttpResponse> observer = new ObserverWithLatch<HttpResponse>();
        observable.subscribe(observer);
        observer.await();
        assertTrue(observer.error instanceof io.netty.channel.ConnectTimeoutException);
    }
    
    @Test
    public void testSameServerObservable() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1");
        RxNettyHttpClient observableClient = new RxNettyHttpClient(config);
        HttpRequest request = HttpRequest.newBuilder().uri("http://www.google.com:81/").build();
        LoadBalancerObservables lbObservables = new LoadBalancerObservables(config, observableClient);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(2);
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
        Observable<HttpResponse> observableWithRetries = lbObservables.executeSameServer(request, TypeDef.fromClass(HttpResponse.class));
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
        RxNettyHttpClient observableClient = new RxNettyHttpClient(config);
        HttpRequest request = HttpRequest.newBuilder().uri("http://www.google.com:80/").build();
        LoadBalancerObservables lbObservables = new LoadBalancerObservables(config, observableClient);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(1);
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
        Observable<HttpResponse> observableWithRetries = lbObservables.executeSameServer(request, TypeDef.fromClass(HttpResponse.class));
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
        RxNettyHttpClient observableClient = new RxNettyHttpClient(config);
        URI uri = new URI("/testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        
        LoadBalancerObservables lbObservables = new LoadBalancerObservables(config, observableClient);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        Server badServer = new Server("localhost:12345");
        Server goodServer = new Server("localhost:" + port);
        List<Server> servers = Lists.newArrayList(badServer, badServer, goodServer);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(1);
        lbObservables.setMaxAutoRetriesNextServer(3);
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
        Observable<Person> observableWithRetries = lbObservables.execute(request, TypeDef.fromClass(Person.class));
        ObserverWithLatch<Person> observer = new ObserverWithLatch<Person>();
        observableWithRetries.subscribe(observer);
        observer.await();
        assertEquals(EmbeddedResources.defaultPerson, observer.obj);
        ServerStats stats = lbObservables.getServerStats(badServer);
        // two requests to bad server because retry same server is set to 1
        assertEquals(2, stats.getTotalRequestsCount());
        assertEquals(0, stats.getActiveRequestsCount());
        assertEquals(2, stats.getSuccessiveConnectionFailureCount());
        
        stats = lbObservables.getServerStats(goodServer);
        // two requests to bad server because retry same server is set to 1
        assertEquals(1, stats.getTotalRequestsCount());
        assertEquals(0, stats.getActiveRequestsCount());
        assertEquals(0, stats.getSuccessiveConnectionFailureCount());
    }
    
    @Test
    public void testObservableWithMultipleServersFailed() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1000");
        RxNettyHttpClient observableClient = new RxNettyHttpClient(config);
        URI uri = new URI("/testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        
        LoadBalancerObservables lbObservables = new LoadBalancerObservables(config, observableClient);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        Server badServer = new Server("localhost:12345");
        Server badServer1 = new Server("localhost:12346");
        Server badServer2 = new Server("localhost:12347");

        List<Server> servers = Lists.newArrayList(badServer, badServer1, badServer2);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        lbObservables.setMaxAutoRetries(1);
        lbObservables.setMaxAutoRetriesNextServer(3);
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
        Observable<Person> observableWithRetries = lbObservables.execute(request, TypeDef.fromClass(Person.class));
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



    
    /*
    @Test
    public void testStream() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/stream").build();
        final List<String> results = Lists.newArrayList();
        final CountDownLatch latch = new CountDownLatch(1);
        client.execute(request, new SSEDecoder(), new ResponseCallback<HttpResponse, String>() {
            @Override
            public void completed(HttpResponse response) {
                latch.countDown();    
            }

            @Override
            public void failed(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void contentReceived(String element) {
                System.out.println("received: " + element);
                results.add(element);
            }

            @Override
            public void cancelled() {
            }

            @Override
            public void responseReceived(HttpResponse response) {
            }
        });
        latch.await(60, TimeUnit.SECONDS); // NOPMD
        assertEquals(EmbeddedResources.streamContent, results);
    }
    
    
    @Test
    public void testNoEntity() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/noEntity");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        final AtomicInteger responseCode = new AtomicInteger();
        final AtomicBoolean hasEntity = new AtomicBoolean(true);
        ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch() {
            @Override
            public void completed(HttpResponse response) {
                super.completed(response);
                responseCode.set(response.getStatus());
                hasEntity.set(response.hasEntity());
            }            
        };
        client.execute(request, TypeDef.fromClass(HttpResponse.class)).addListener(callback);
        callback.awaitCallback();
        assertNull(callback.getError());
        assertEquals(200, responseCode.get());
        assertFalse(hasEntity.get());
    }


    @Test
    public void testPost() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        Person myPerson = new Person("netty", 5);
        HttpRequest request = HttpRequest.newBuilder().uri(uri).verb(Verb.POST).entity(myPerson).header("Content-type", "application/json").build();
        ResponseCallbackWithLatchForType<Person> callback = new ResponseCallbackWithLatchForType<Person>();        
        client.execute(request, TypeDef.fromClass(Person.class)).addListener(callback);
        callback.awaitCallback();        
        assertEquals(myPerson, callback.get());
    }

    @Test
    public void testQuery() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/personQuery");
        Person myPerson = new Person("hello world", 4);
        HttpRequest request = HttpRequest.newBuilder().uri(uri).queryParams("age", String.valueOf(myPerson.age))
                .queryParams("name", myPerson.name).build();
        
        ResponseCallbackWithLatchForType<Person> callback = new ResponseCallbackWithLatchForType<Person>();        
        client.execute(request, TypeDef.fromClass(Person.class)).addListener(callback);
        callback.awaitCallback();        
        assertEquals(myPerson, callback.get());
    }

    @Test
    public void testConnectTimeout() throws Exception {
        AsyncNettyHttpClient timeoutClient = new AsyncNettyHttpClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1"));
        HttpRequest request = HttpRequest.newBuilder().uri("http://www.google.com:81/").build();
        ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch();        
        timeoutClient.execute(request, TypeDef.fromClass(HttpResponse.class)).addListener(callback);
        callback.awaitCallback();
        assertNotNull(callback.getError());
    }

    @Test
    public void testReadTimeout() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/readTimeout");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch();        
        client.execute(request, TypeDef.fromClass(HttpResponse.class)).addListener(callback);
        callback.awaitCallback();
        assertNotNull(callback.getError());
    }
    
    @Test
    public void testFuture() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        Future<HttpResponse> future = client.execute(request, TypeDef.fromClass(HttpResponse.class));
        HttpResponse response = future.get();
        // System.err.println(future.get().getEntity(Person.class));
        Person person = response.getEntity(Person.class);
        assertEquals(EmbeddedResources.defaultPerson, person);
        assertTrue(response.getHeaders().get("Content-type").contains("application/json"));
    }
    
    @Test
    public void testThrottle() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/throttle");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ResponseCallbackWithLatchForType<Person> callback = new  ResponseCallbackWithLatchForType<Person>();
        client.execute(request, TypeDef.fromClass(Person.class)).addListener(callback);
        callback.awaitCallback();
        assertEquals("Service Unavailable", callback.getError().getMessage());
    }
    
    
    @Test
    public void testCancelWithLongRead() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/readTimeout");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch();        
        Future<HttpResponse> future = client.execute(request, TypeDef.fromClass(HttpResponse.class)).addListener(callback);
        // wait until channel is established
        Thread.sleep(2000);
        assertTrue(future.cancel(true));
        callback.awaitCallback();
        assertTrue(callback.isCancelled());
        try {
            future.get();
            fail("Exception expected since future is cancelled");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }
    
    @Test
    public void testCancelWithConnectionIssue() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri("http://www.google.com:81/").build();
        ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch();        
        Future<HttpResponse> future = client.execute(request, callback);
        assertTrue(future.cancel(true));
        callback.awaitCallback();
        assertTrue(callback.isCancelled());
        try {
            future.get();
            fail("Exception expected since future is cancelled");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }
    
    @Test
    public void testObservable() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ObservableAsyncClient<HttpRequest, HttpResponse, ByteBuf> observableClient = 
               new ObservableAsyncClient<HttpRequest, HttpResponse, ByteBuf>(client);
        final List<Person> result = Lists.newArrayList();
        observableClient.execute(request).toBlockingObservable().forEach(new Action1<HttpResponse>() {
            @Override
            public void call(HttpResponse t1) {
                try {
                    result.add(t1.getEntity(Person.class));
                } catch (Exception e) { // NOPMD
                }
            }
        });
        assertEquals(Lists.newArrayList(EmbeddedResources.defaultPerson), result);
        assertEquals(EmbeddedResources.defaultPerson, observableClient.execute(request).toBlockingObservable().single().getEntity(Person.class));
    }
    
    @Test
    public void testStreamObservable() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/stream").build();
        final List<String> results = Lists.newArrayList();
        ObservableAsyncClient<HttpRequest, HttpResponse, ByteBuf> observableClient = 
                new ObservableAsyncClient<HttpRequest, HttpResponse, ByteBuf>(client);
        observableClient.stream(request, new SSEDecoder())
            .toBlockingObservable()
            .forEach(new Action1<StreamEvent<HttpResponse, String>>() {

                @Override
                public void call(final StreamEvent<HttpResponse, String> t1) {
                    results.add(t1.getEvent());
                }
            });                
        assertEquals(EmbeddedResources.streamContent, results);
    }
    
    @Test
    public void testLoadBalancingClient() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuf, ContentTypeBasedSerializerKey> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuf, ContentTypeBasedSerializerKey>(client);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        List<Server> servers = Lists.newArrayList(new Server("localhost:" + port));
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch();        
        loadBalancingClient.execute(request, callback);
        callback.awaitCallback();        
        assertEquals(EmbeddedResources.defaultPerson, callback.getHttpResponse().getEntity(Person.class));
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(new Server("localhost:" + port)).getTotalRequestsCount());        
    }
    
    @Test
    public void testLoadBalancingClientFuture() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuf, ContentTypeBasedSerializerKey> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuf, ContentTypeBasedSerializerKey>(client);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        List<Server> servers = Lists.newArrayList(new Server("localhost:" + port));
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/testAsync/person");
        Person person = null;
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        Future<HttpResponse> future = loadBalancingClient.execute(request);
        HttpResponse response = future.get();
        person = response.getEntity(Person.class);
        assertEquals(EmbeddedResources.defaultPerson, person);
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(new Server("localhost:" + port)).getTotalRequestsCount());        
    }

    
    @Test
    public void testLoadBalancingClientMultiServers() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuf, ?> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuf, ContentTypeBasedSerializerKey>(client);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new RoundRobinRule());
        Server good = new Server("localhost:" + port);
        Server bad = new Server("localhost:" + 33333);
        List<Server> servers = Lists.newArrayList(bad, bad, good);
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        loadBalancingClient.setMaxAutoRetriesNextServer(2);
        URI uri = new URI("/testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch();        
        loadBalancingClient.execute(request, callback);
        callback.awaitCallback();       
        assertEquals(EmbeddedResources.defaultPerson, callback.getHttpResponse().getEntity(Person.class));
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(good).getTotalRequestsCount());
    }
    
    @Test
    public void testLoadBalancingClientConcurrency() throws Exception {
        final AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuf, ?> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuf, ContentTypeBasedSerializerKey>(client);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new RoundRobinRule());
        final Server good = new Server("localhost:" + port);
        final Server bad = new Server("localhost:" + 33333);
        List<Server> servers = Lists.newArrayList(good, bad, good, good);
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        loadBalancingClient.setMaxAutoRetriesNextServer(2);
        URI uri = new URI("/testAsync/person");
        final HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        int concurrency = 200;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        final CountDownLatch completeLatch = new CountDownLatch(concurrency);
        for (int i = 0; i < concurrency; i++) {
            final int num = i;
            final ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch();        
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        loadBalancingClient.execute(request, callback);
                        callback.awaitCallback();       
                        completeLatch.countDown();
                    } catch (Throwable e) {
                        e.printStackTrace();
                        fail(e.getMessage());
                    }
                    if (callback.getError() == null) {
                        try {
                            assertEquals(EmbeddedResources.defaultPerson, callback.getHttpResponse().getEntity(Person.class));
                        } catch (Exception e) {
                            e.printStackTrace();
                            fail(e.getMessage());
                        }
                    }
                }
            });     
        }
        if (!completeLatch.await(60, TimeUnit.SECONDS)) {
            fail("Not all threads have completed");
        }
    }

    @Test
    public void testLoadBalancingClientMultiServersFuture() throws Exception {
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new RoundRobinRule());
        Server good = new Server("localhost:" + port);
        Server bad = new Server("localhost:" + 33333);
        List<Server> servers = Lists.newArrayList(bad, bad, good);
        lb.setServersList(servers);
        AsyncLoadBalancingClient<HttpRequest, 
        HttpResponse, ByteBuf, ContentTypeBasedSerializerKey> lbClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuf, ContentTypeBasedSerializerKey>(client); 
        lbClient.setLoadBalancer(lb);        
        lbClient.setMaxAutoRetriesNextServer(2);
        URI uri = new URI("/testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        Future<HttpResponse> future = lbClient.execute(request);
        assertEquals(EmbeddedResources.defaultPerson, future.get().getEntity(Person.class));
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(good).getTotalRequestsCount());
    }

    @Test
    public void testParallel() throws Exception {
        Server good = new Server("localhost:" + port);
        Server bad = new Server("localhost:" + 33333);
        List<Server> servers = Lists.newArrayList(bad, bad, good, good);
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuf, ContentTypeBasedSerializerKey> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuf, ContentTypeBasedSerializerKey>(client); 
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new RoundRobinRule());
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch();                
        ExecutionResult<HttpResponse> result = loadBalancingClient.executeWithBackupRequests(request, 4, 1, TimeUnit.MILLISECONDS,null, callback);
        callback.awaitCallback();
        assertTrue(result.isResponseReceived());
        assertNull(callback.getError());
        assertFalse(callback.isCancelled());
        assertEquals(EmbeddedResources.defaultPerson, callback.getHttpResponse().getEntity(Person.class));
        
        // System.err.println("test done");
        for (Future<HttpResponse> future: result.getAllAttempts().values()) {
            if (!future.isDone()) {
                fail("All futures should be done at this point");
            }
        }
        assertEquals(new URI("http://" + good.getHost() + ":" + good.getPort() +  uri.getPath()), result.getExecutedURI());
    }
    
    @Test
    public void testParallelAllFailed() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuf, ContentTypeBasedSerializerKey> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuf, ContentTypeBasedSerializerKey>(client);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new RoundRobinRule());
        Server bad = new Server("localhost:" + 55555);
        Server bad1 = new Server("localhost:" + 33333);
        List<Server> servers = Lists.newArrayList(bad, bad1, bad);
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch();                
        ExecutionResult<HttpResponse> result = loadBalancingClient.executeWithBackupRequests(request, 2, 1, TimeUnit.MILLISECONDS, null, callback);
        // make sure we do not get more than 1 callback
        callback.awaitCallback();
        assertNotNull(callback.getError());
        assertTrue(result.isFailed());
    }
    
    @Test
    public void testLoadBalancingClientWithRetry() throws Exception {
        
        AsyncNettyHttpClient timeoutClient = 
                new AsyncNettyHttpClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1"));
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuf, ContentTypeBasedSerializerKey> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuf, ContentTypeBasedSerializerKey>(timeoutClient);
        loadBalancingClient.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
        loadBalancingClient.setMaxAutoRetries(1);
        loadBalancingClient.setMaxAutoRetriesNextServer(1);
        Server server = new Server("www.microsoft.com:81");
        List<Server> servers = Lists.newArrayList(server);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ResponseCallbackWithLatch callback = new ResponseCallbackWithLatch();        
        loadBalancingClient.execute(request, callback);
        callback.awaitCallback();        
        assertNotNull(callback.getError());
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(server).getTotalRequestsCount());                
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(server).getSuccessiveConnectionFailureCount());                
    }
    
   
    @Test
    public void testLoadBalancingClientWithRetryFuture() throws Exception {
        AsyncNettyHttpClient timeoutClient = 
                new AsyncNettyHttpClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1"));
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuf, ContentTypeBasedSerializerKey> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuf, ContentTypeBasedSerializerKey>(timeoutClient);
        loadBalancingClient.setErrorHandler(new NettyHttpLoadBalancerErrorHandler());
        loadBalancingClient.setMaxAutoRetries(1);
        loadBalancingClient.setMaxAutoRetriesNextServer(1);
        Server server = new Server("www.microsoft.com:81");
        List<Server> servers = Lists.newArrayList(server);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        Future<HttpResponse> future = loadBalancingClient.execute(request);
        try {
            future.get();
            fail("ExecutionException expected");
        } catch (Exception e) {
            assertTrue(e instanceof ExecutionException);
        }
            
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(server).getTotalRequestsCount());                
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(server).getSuccessiveConnectionFailureCount());                
    }
    
    @Test
    public void testConcurrentStreaming() throws Exception {
        final HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/stream").build();
        int concurrency = 200;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        final CountDownLatch completeLatch = new CountDownLatch(concurrency);
        final AtomicInteger successCount = new AtomicInteger();
        for (int i = 0; i < concurrency; i++) {
            final int num = i;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    final List<String> results = Lists.newArrayList();
                    final CountDownLatch latch = new CountDownLatch(1);
                    final AtomicBoolean failed = new AtomicBoolean();
                    try {
                        client.execute(request, new SSEDecoder(), new ResponseCallback<HttpResponse, String>() {
                            @Override
                            public void completed(HttpResponse response) {
                                latch.countDown();    
                            }

                            @Override
                            public void failed(Throwable e) {
                                System.err.println("Error received " + e);
                                failed.set(true);
                                latch.countDown();
                            }

                            @Override
                            public void contentReceived(String element) {
                                results.add(element);
                            }

                            @Override
                            public void cancelled() {
                            }

                            @Override
                            public void responseReceived(HttpResponse response) {
                            }
                        });
                        if (!latch.await(60, TimeUnit.SECONDS)) {
                            fail("No callback happens within timeout");
                        }
                        if (!failed.get()) {
                            successCount.incrementAndGet();
                            assertEquals(EmbeddedResources.streamContent, results);
                        } else {
                            System.err.println("Thread " + num + " failed");
                        }
                        completeLatch.countDown();
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail("Thread " + num + " failed due to unexpected exception");
                    }
                    
                }
            });
        }
        if (!completeLatch.await(60, TimeUnit.SECONDS)) {
            fail("Some threads have not completed streaming within timeout");
        }
        System.out.println("Successful streaming " + successCount.get());
    }
    */

}
