/*
 *
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.client.netty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.client.HttpRequest;
import io.reactivex.netty.protocol.http.client.HttpResponse;

import java.net.URI;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import rx.Observable;
import rx.util.functions.Action1;
import rx.util.functions.Func1;

import com.google.common.collect.Lists;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.client.ClientException;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.UnexpectedHttpResponseException;
import com.netflix.client.netty.http.NettyHttpClient;
import com.netflix.client.netty.http.NettyHttpClientBuilder;
import com.netflix.client.netty.http.NettyHttpClientBuilder.NettyHttpLoadBalancingClientBuilder;
import com.netflix.client.netty.http.NettyHttpLoadBalancerErrorHandler;
import com.netflix.client.netty.http.NettyHttpLoadBalancingClient;
import com.netflix.client.netty.http.ServerSentEventWithEntity;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.ribbon.test.resources.EmbeddedResources;
import com.netflix.ribbon.test.resources.EmbeddedResources.Person;
import com.netflix.serialization.JacksonCodec;
import com.netflix.serialization.SerializationUtils;
import com.netflix.serialization.StringDeserializer;
import com.netflix.serialization.TypeDef;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.net.httpserver.HttpServer;

public class NettyClientTest {
    
    private static HttpServer server = null;
    private static String SERVICE_URI;
    private static int port;
    private static final String host = "localhost";
    
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
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/person");
        NettyHttpClient observableClient = NettyHttpClientBuilder.newBuilder()
                .build();
        final List<Person> result = Lists.newArrayList();
        observableClient.createEntityObservable(host, port, request, TypeDef.fromClass(Person.class), null).toBlockingObservable().forEach(new Action1<Person>() {
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
        DefaultClientConfigImpl overrideConfig = new DefaultClientConfigImpl();
        overrideConfig.setPropertyWithType(CommonClientConfigKey.Deserializer, StringDeserializer.getInstance());
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/person");
        NettyHttpClient observableClient = new NettyHttpClient();
        final List<String> result = Lists.newArrayList();
        observableClient.createEntityObservable(host, port, request, TypeDef.fromClass(String.class), overrideConfig).toBlockingObservable().forEach(new Action1<String>() {
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
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/person");
        NettyHttpClient observableClient = new NettyHttpClient();
        Observable<Person> observable = observableClient.createEntityObservable(host, port, request, TypeDef.fromClass(Person.class), null).cache();
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
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/person");
        NettyHttpClient observableClient = new NettyHttpClient();
        final List<Person> result = Lists.newArrayList();
        final CountDownLatch latch = new CountDownLatch(1);        
        observableClient.createFullHttpResponseObservable(host, port, request, null)      
         .flatMap(new Func1<HttpResponse<ByteBuf>, Observable<Person>>() {
            @Override
            public Observable<Person> call(final HttpResponse<ByteBuf> t1) {
                  return t1.getContent().map(new Func1<ByteBuf, Person>() {
                        @Override
                        public Person call(ByteBuf input) {
                            if (input.isReadable()) {
                                try {
                                    return JacksonCodec.<Person>getInstance().deserialize(new ByteBufInputStream(input), TypeDef.fromClass(Person.class));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                return null;
                            }
                        }
                    });
                } 
            })
        .subscribe(new Action1<Person>() {
            @Override
            public void call(Person person) {
                try {
                    result.add(person);
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
    /*
    @Test
    public void testBlockingFullResponse() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/person");
        NettyHttpClient observableClient = new NettyHttpClient();
        HttpResponse response = observableClient.execute(host, port, request, TypeDef.fromClass(HttpResponse.class), null);
        try {
            Person person = response.getEntity(TypeDef.fromClass(Person.class), JacksonCodec.<Person>getInstance());
            assertEquals(EmbeddedResources.defaultPerson, person);
        } finally {
            response.close();
        }
    }
    */
    /*
    @Test
    public void testFullResponseWithDeserializer() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/person");
        NettyHttpClient observableClient = new NettyHttpClient();
        final List<Person> result = Lists.newArrayList();
        final CountDownLatch latch = new CountDownLatch(1);        
        observableClient.createEntityObservable(host, port, request, TypeDef.fromClass(HttpResponse.class), null).subscribe(new Action1<HttpResponse>() {
            @Override
            public void call(HttpResponse t1) {
                try {
                    result.add(t1.getEntity(TypeDef.fromClass(Person.class), JacksonCodec.<Person>getInstance()));
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
*/
    
    @Test
    public void testPostWithObservable() throws Exception {
        Person myPerson = new Person("netty", 5);
        HttpRequest<ByteBuf> request = HttpRequest.createPost(SERVICE_URI + "testAsync/person")
                .withHeader("Content-type", "application/json")
                .withContent(SerializationUtils.serializeToBytes(JacksonCodec.getInstance(), myPerson, null));
        NettyHttpClient observableClient = new NettyHttpClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues().setPropertyWithType(CommonClientConfigKey.ReadTimeout, 1000000));
        final List<Person> result = Lists.newArrayList();
        observableClient.createEntityObservable(host, port, request, TypeDef.fromClass(Person.class), null).toBlockingObservable().forEach(new Action1<Person>() {
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
    public void testPostWithByteBuf() throws Exception {
        Person myPerson = new Person("netty", 5);
        ObjectMapper mapper = new ObjectMapper();
        byte[] raw = mapper.writeValueAsBytes(myPerson);
        ByteBuf buffer = Unpooled.copiedBuffer(raw);
        HttpRequest<ByteBuf> request = HttpRequest.createPost(SERVICE_URI + "testAsync/person")
                .withHeader("Content-type", "application/json")
                .withHeader("Content-length", String.valueOf(raw.length))
                .withContent(buffer);
        NettyHttpClient observableClient = new NettyHttpClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues().setPropertyWithType(CommonClientConfigKey.ReadTimeout, 1000000));
        final List<Person> result = Lists.newArrayList();
        observableClient.createEntityObservable(host, port, request, TypeDef.fromClass(Person.class), null).toBlockingObservable().forEach(new Action1<Person>() {
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
        NettyHttpClient observableClient = new NettyHttpClient(
                DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1"));
        HttpRequest<ByteBuf> request = HttpRequest.createGet("http://www.google.com:81/");
        Observable<HttpResponse<ByteBuf>> observable = observableClient.createFullHttpResponseObservable("www.google.com", 81, request);
        
        ObserverWithLatch<HttpResponse<ByteBuf>> observer = new ObserverWithLatch<HttpResponse<ByteBuf>>();
        observable.subscribe(observer);
        observer.await();
        assertNotNull(observer.error);
        assertTrue(observer.error instanceof io.netty.channel.ConnectTimeoutException);
    }
    
    @Test
    public void testReadTimeout() throws Exception {
        NettyHttpClient observableClient = new NettyHttpClient(
                DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ReadTimeout, "100"));
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/readTimeout");
        Observable<HttpResponse<ByteBuf>> observable = observableClient.createFullHttpResponseObservable(host, port, request);
        ObserverWithLatch<HttpResponse<ByteBuf>> observer = new ObserverWithLatch<HttpResponse<ByteBuf>>();
        observable.subscribe(observer);
        observer.await();
        assertTrue(observer.error instanceof io.netty.handler.timeout.ReadTimeoutException);      
        Observable<Person> person = observableClient.createEntityObservable(host, port, request, TypeDef.fromClass(Person.class), null);
        ObserverWithLatch<Person> personObserver = new ObserverWithLatch<Person>();
        person.subscribe(personObserver);
        personObserver.await();
        assertTrue(personObserver.error instanceof io.netty.handler.timeout.ReadTimeoutException);      
    }
    
    @Test
    public void testObservableWithMultipleServers() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1000");
        HttpRequest<ByteBuf> request = HttpRequest.createGet("/testAsync/person");
        
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        Server badServer = new Server("localhost:12345");
        Server goodServer = new Server("localhost:" + port);
        List<Server> servers = Lists.newArrayList(badServer, badServer, badServer, goodServer);
        NettyHttpLoadBalancingClient lbObservables = NettyHttpLoadBalancingClientBuilder.newBuilder()
                .withClientConfig(config)
                .withLoadBalancer(lb)
                .withFixedServerList(servers)
                .withLoadBalancerErrorHandler(new NettyHttpLoadBalancerErrorHandler(1, 3, true))
                .build();
        Observable<Person> observableWithRetries = lbObservables.createEntityObservable(request, TypeDef.fromClass(Person.class), null, null);
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
    public void testExecutionWithMultipleServers() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1000");
        HttpRequest<ByteBuf> request = HttpRequest.createGet("/testAsync/person");
        
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        Server badServer = new Server("localhost:12345");
        Server goodServer = new Server("localhost:" + port);
        List<Server> servers = Lists.newArrayList(badServer, badServer, badServer, goodServer);
        NettyHttpLoadBalancingClient lbObservables = NettyHttpLoadBalancingClientBuilder.newBuilder()
                .withClientConfig(config)
                .withLoadBalancer(lb)
                .withFixedServerList(servers)
                .withLoadBalancerErrorHandler(new NettyHttpLoadBalancerErrorHandler(1, 3, true))
                .build();

        
        Person p = lbObservables.createEntityObservable(request, TypeDef.fromClass(Person.class), null, null).toBlockingObservable().single();
        
        assertEquals(EmbeddedResources.defaultPerson, p);
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
        NettyHttpLoadBalancerErrorHandler errorHandler = new NettyHttpLoadBalancerErrorHandler(1, 3, true);
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1000");
        HttpRequest<ByteBuf> request = HttpRequest.createGet("/testAsync/person");
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());        
        NettyHttpLoadBalancingClient lbObservables = new NettyHttpLoadBalancingClient(lb, config, errorHandler);
        Server badServer = new Server("localhost:12345");
        Server goodServer = new Server("localhost:" + port);
        List<Server> servers = Lists.newArrayList(badServer, badServer, badServer, goodServer);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        Observable<HttpResponse<ByteBuf>> observableWithRetries = lbObservables.createFullHttpResponseObservable(request, null, null);
        ObserverWithLatch<HttpResponse<ByteBuf>> observer = new ObserverWithLatch<HttpResponse<ByteBuf>>();
        observableWithRetries.subscribe(observer);
        observer.await();
        assertEquals(200, observer.obj.getStatus().code());
    }

    
    @Test
    public void testLoadBalancingObservablesWithReadTimeout() throws Exception {
        NettyHttpLoadBalancerErrorHandler errorHandler = new NettyHttpLoadBalancerErrorHandler(1, 3, true);
        MockWebServer server = new MockWebServer();
        String content = "{\"name\": \"ribbon\", \"age\": 2}";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "application/json")
                .setBody(content));       
        server.play();

        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues()
                .setPropertyWithType(CommonClientConfigKey.ReadTimeout, 100);
        HttpRequest<ByteBuf> request = HttpRequest.createGet("/testAsync/readTimeout");
        
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        NettyHttpLoadBalancingClient lbObservables = new NettyHttpLoadBalancingClient(lb, config, errorHandler);
        Server goodServer = new Server("localhost:" + server.getPort());
        Server badServer = new Server("localhost:" + port);
        List<Server> servers = Lists.newArrayList(goodServer, badServer, badServer, goodServer);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        Observable<Person> observableWithRetries = lbObservables.createEntityObservable(request, TypeDef.fromClass(Person.class), null, null);
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
    
    //TODO: this does not work as the ByteBuf is already written and we need to recreate the ByteBuf for every request
    @Ignore
    public void testLoadBalancingPostWithReadTimeout() throws Exception {
        MockWebServer server = new MockWebServer();
        String content = "{\"name\": \"ribbon\", \"age\": 2}";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "application/json")
                .setBody(content));       
        server.play();

        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues()
                .setPropertyWithType(CommonClientConfigKey.ReadTimeout, 1000);
        HttpRequest<ByteBuf> request = HttpRequest.createPost("/testAsync/postTimeout")
                .withContent(SerializationUtils.serializeToBytes(JacksonCodec.getInstance(), EmbeddedResources.defaultPerson, null))
                .withHeader("Content-type", "application/json");
        NettyHttpLoadBalancerErrorHandler errorHandler = new NettyHttpLoadBalancerErrorHandler(1, 3, true);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        NettyHttpLoadBalancingClient lbObservables = new NettyHttpLoadBalancingClient(lb, config, errorHandler);
        Server goodServer = new Server("localhost:" + server.getPort());
        Server badServer = new Server("localhost:" + port);
        List<Server> servers = Lists.newArrayList(goodServer, badServer, goodServer, goodServer);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        RetryHandler handler = new RequestSpecificRetryHandler(true, true, errorHandler, null) {
            @Override
            public boolean isRetriableException(Throwable e, boolean sameServer) {
                return true;
            }
        };
        Observable<Person> observableWithRetries = lbObservables.createEntityObservable(request, TypeDef.fromClass(Person.class), null, handler, null);
        ObserverWithLatch<Person> observer = new ObserverWithLatch<Person>();
        observableWithRetries.subscribe(observer);
        observer.await();
        if (observer.error != null) {
            observer.error.printStackTrace();
        }
        assertEquals("ribbon", observer.obj.name);
        assertEquals(2, observer.obj.age);
        ServerStats stats = lbObservables.getServerStats(badServer);
        server.shutdown();
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
        NettyHttpLoadBalancerErrorHandler errorHandler = new NettyHttpLoadBalancerErrorHandler(1, 3, true);
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "100");
        HttpRequest<ByteBuf> request = HttpRequest.createGet("/testAsync/person");
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());        
        NettyHttpLoadBalancingClient lbObservables = new NettyHttpLoadBalancingClient(lb, config, errorHandler);
        Server badServer = new Server("localhost:12345");
        Server badServer1 = new Server("localhost:12346");
        Server badServer2 = new Server("localhost:12347");

        List<Server> servers = Lists.newArrayList(badServer, badServer1, badServer2);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        Observable<Person> observableWithRetries = lbObservables.createEntityObservable(request, TypeDef.fromClass(Person.class), null, null);
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
        IClientConfig overrideConfig = new DefaultClientConfigImpl().setPropertyWithType(CommonClientConfigKey.Deserializer, JacksonCodec.getInstance());
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/personStream");
        NettyHttpClient observableClient = new NettyHttpClient();
        final List<Person> result = Lists.newArrayList();
        observableClient.createServerSentEventEntityObservable(host, port, request, TypeDef.fromClass(Person.class), overrideConfig).subscribe(new Action1<ServerSentEventWithEntity<Person>>() {
            @Override
            public void call(ServerSentEventWithEntity<Person> t1) {
                // System.out.println(t1);
                result.add(t1.getEntity());
            }
        }, new Action1<Throwable>() {

            @Override
            public void call(Throwable t1) {
                t1.printStackTrace();
            }
            
        });
        Thread.sleep(5000);
        assertEquals(EmbeddedResources.entityStream, result);
    }
    
    
    @Test
    public void testStreamWithLoadBalancer() throws Exception {
        NettyHttpLoadBalancerErrorHandler errorHandler = new NettyHttpLoadBalancerErrorHandler(1, 3, true);
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1000");
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        NettyHttpLoadBalancingClient lbObservables = new NettyHttpLoadBalancingClient(lb, config, errorHandler);
        HttpRequest<ByteBuf> request = HttpRequest.createGet("/testAsync/personStream");
        final List<Person> result = Lists.newArrayList();
        Server goodServer = new Server("localhost:" + port);
        Server badServer = new Server("localhost:12245");
        List<Server> servers = Lists.newArrayList(badServer, badServer, badServer, goodServer);
        lb.setServersList(servers);
        lbObservables.setLoadBalancer(lb);
        IClientConfig requestConfig = DefaultClientConfigImpl.getEmptyConfig().setPropertyWithType(CommonClientConfigKey.Deserializer, JacksonCodec.<Person>getInstance());

        lbObservables.createServerSentEventEntityObservable(request, TypeDef.fromClass(Person.class), requestConfig, null).toBlockingObservable().forEach(new Action1<ServerSentEventWithEntity<Person>>() {
            @Override
            public void call(ServerSentEventWithEntity<Person> t1) {
                result.add(t1.getEntity());
            }
        });
        assertEquals(EmbeddedResources.entityStream, result);
    }

    @Test
    public void testQuery() throws Exception {
        Person myPerson = new Person("hello_world", 4);
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/personQuery?name=" + myPerson.name + "&age=" + myPerson.age);
        NettyHttpClient observableClient = new NettyHttpClient();
        final List<Person> result = Lists.newArrayList();
        observableClient.createEntityObservable(host, port, request, TypeDef.fromClass(Person.class), null).toBlockingObservable().forEach(new Action1<Person>() {
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
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/throttle");
        NettyHttpClient client = new NettyHttpClient();
        final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
        Person p = client.createEntityObservable(host, port, request, TypeDef.fromClass(Person.class), null).onErrorReturn(new Func1<Throwable, Person>() {
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
    }
    
    @Test
    public void testUnexpectedResponse() throws Exception {
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/throttle");
        NettyHttpClient client = new NettyHttpClient();
        Observable<HttpResponse<ByteBuf>> responseObservable = client.createFullHttpResponseObservable(host, port, request);
        final AtomicReference<HttpResponse<ByteBuf>> response = new AtomicReference<HttpResponse<ByteBuf>>();
        responseObservable.toBlockingObservable().forEach(new Action1<HttpResponse<ByteBuf>>() {
            @Override
            public void call(HttpResponse<ByteBuf> t1) {
                response.set(t1);
            }
        });
        assertEquals(503, response.get().getStatus().code());
    }  
    
}
