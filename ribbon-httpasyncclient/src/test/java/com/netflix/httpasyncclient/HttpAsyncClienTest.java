package com.netflix.httpasyncclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.nio.util.ExpandableBuffer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.junit.BeforeClass;
import org.junit.Test;

import rx.util.functions.Action1;

import com.google.common.collect.Lists;
import com.netflix.client.AsyncLoadBalancingClient;
import com.netflix.client.ClientException;
import com.netflix.client.FullResponseCallback;
import com.netflix.client.ObservableAsyncClient;
import com.netflix.client.ObservableAsyncClient.StreamEvent;
import com.netflix.client.ResponseCallback;
import com.netflix.client.StreamDecoder;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.client.HttpResponse;
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
    
    static class ExpandableByteBuffer extends ExpandableBuffer {
        public ExpandableByteBuffer(int size) {
            super(size, HeapByteBufferAllocator.INSTANCE);
        }

        public ExpandableByteBuffer() {
            super(4 * 1024, HeapByteBufferAllocator.INSTANCE);
        }

        public void addByte(byte b) {
            if (this.buffer.remaining() == 0) {
                expand();
            }
            this.buffer.put(b);
        }

        public boolean hasContent() {
            return this.buffer.position() > 0;
        }

        public byte[] getBytes() {
            byte[] data = new byte[this.buffer.position()];
            this.buffer.position(0);
            this.buffer.get(data);
            return data;
        }

        public void reset() {
            clear();
        }

        public void consumeInputStream(InputStream content) throws IOException {
            try {
                int b = -1;
                while ((b = content.read()) != -1) {
                    addByte((byte) b);
                }
            } finally {
                content.close();
            }
        }
    }
    
    static class SSEDecoder implements StreamDecoder<List<String>, ByteBuffer> {
        final ExpandableByteBuffer dataBuffer = new ExpandableByteBuffer();

        @Override
        public List<String> decode(ByteBuffer buf) throws IOException {
            List<String> result = Lists.newArrayList();
            while (buf.position() < buf.limit()) {
                byte b = buf.get();
                if (b == 10 || b == 13) {
                    if (dataBuffer.hasContent()) {
                        result.add(new String(dataBuffer.getBytes()));
                    }
                    dataBuffer.reset();
                } else {
                    dataBuffer.addByte(b);
                }
            }
            return result;
        }
        
    }

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
        final AtomicReference<HttpResponse> res = new AtomicReference<HttpResponse>();
        client.execute(request, new FullResponseCallback<HttpResponse>() {            
            @Override
            public void completed(HttpResponse response) {
                try {
                    res.set(response);
                    person = response.get(Person.class);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void failed(Throwable e) {
                exception.set(e);
            }

            @Override
            public void cancelled() {
            }
        });
        // System.err.println(future.get().get(Person.class));
        Thread.sleep(2000);
        assertEquals(EmbeddedResources.defaultPerson, person);
        assertNull(exception.get());
        assertTrue(res.get().getHeaders().get("Content-type").contains("application/json"));
    }
    
    @Test
    public void testFuture() throws Exception {
        URI uri = new URI(SERVICE_URI + "testNetty/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        Future<HttpResponse> future = client.execute(request, null);
        HttpResponse response = future.get();
        // System.err.println(future.get().get(Person.class));
        person = response.get(Person.class);
        assertEquals(EmbeddedResources.defaultPerson, person);
        assertTrue(response.getHeaders().get("Content-type").contains("application/json"));
    }

    
    @Test
    public void testObservable() throws Exception {
        URI uri = new URI(SERVICE_URI + "testNetty/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        ObservableAsyncClient<HttpRequest, HttpResponse, ByteBuffer> observableClient = new ObservableAsyncClient<HttpRequest, HttpResponse, ByteBuffer>(client);
        final List<Person> result = Lists.newArrayList();
        observableClient.execute(request).toBlockingObservable().forEach(new Action1<HttpResponse>() {
            @Override
            public void call(HttpResponse t1) {
                try {
                    result.add(t1.get(Person.class));
                } catch (ClientException e) {
                }
            }
        });
        assertEquals(Lists.newArrayList(EmbeddedResources.defaultPerson), result);
        System.err.println(observableClient.execute(request).toBlockingObservable().single().get(Person.class));
    }

    @Test
    public void testNoEntity() throws Exception {
        URI uri = new URI(SERVICE_URI + "testNetty/noEntity");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        final AtomicInteger responseCode = new AtomicInteger();
        final AtomicBoolean hasEntity = new AtomicBoolean(true);
        client.execute(request, new FullResponseCallback<HttpResponse>() {            
            @Override
            public void completed(HttpResponse response) {
                responseCode.set(response.getStatus());
                hasEntity.set(response.hasEntity());
            }
            
            @Override
            public void failed(Throwable e) {
                exception.set(e);
            }

            @Override
            public void cancelled() {
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
        
        client.execute(request, new FullResponseCallback<HttpResponse>() {            
            @Override
            public void completed(HttpResponse response) {
                try {
                    person = response.get(Person.class);
                } catch (ClientException e) { // NOPMD
                }
            }
            
            @Override
            public void failed(Throwable e) {
            }

            @Override
            public void cancelled() {
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
        
        client.execute(request, new FullResponseCallback<HttpResponse>() {            
            @Override
            public void completed(HttpResponse response) {
                try {
                    person = response.get(Person.class);
                } catch (ClientException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void failed(Throwable e) {
            }

            @Override
            public void cancelled() {
                // TODO Auto-generated method stub
                
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
        timeoutClient.execute(request, new FullResponseCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse response) {
                System.err.println("Got response");
            }

            @Override
            public void failed(Throwable e) {
                exception.set(e);
            }

            @Override
            public void cancelled() {
                // TODO Auto-generated method stub
                
            }
            
        });
        Thread.sleep(2000);
        assertNotNull(exception.get());
    }
    
    @Test
    public void testLoadBalancingClient() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuffer> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuffer>(client);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        List<Server> servers = Lists.newArrayList(new Server("localhost:" + port));
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/testNetty/person");
        person = null;
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        loadBalancingClient.execute(request, null, new FullResponseCallback<HttpResponse>() {            
            @Override
            public void completed(HttpResponse response) {
                try {
                    person = response.get(Person.class);
                } catch (ClientException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void failed(Throwable e) {
            }

            @Override
            public void cancelled() {
                // TODO Auto-generated method stub
                
            }
        });
        Thread.sleep(2000);
        assertEquals(EmbeddedResources.defaultPerson, person);
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(new Server("localhost:" + port)).getTotalRequestsCount());        
    }
    
    @Test
    public void testLoadBalancingClientFuture() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuffer> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuffer>(client);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        List<Server> servers = Lists.newArrayList(new Server("localhost:" + port));
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/testNetty/person");
        person = null;
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        Future<HttpResponse> future = loadBalancingClient.execute(request, null, null);
        HttpResponse response = future.get();
        person = response.get(Person.class);
        assertEquals(EmbeddedResources.defaultPerson, person);
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(new Server("localhost:" + port)).getTotalRequestsCount());        
    }

    
    @Test
    public void testLoadBalancingClientMultiServers() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuffer> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuffer>(client);
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
        loadBalancingClient.execute(request, null, new FullResponseCallback<HttpResponse>() {            
            @Override
            public void completed(HttpResponse response) {
                try {
                    person = response.get(Person.class);
                } catch (ClientException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void failed(Throwable e) {
                exception.set(e);
            }

            @Override
            public void cancelled() {
                // TODO Auto-generated method stub
                
            }
        });
        Thread.sleep(10000);
        assertNull(exception.get());
        assertEquals(EmbeddedResources.defaultPerson, person);
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(good).getTotalRequestsCount());
    }
    
    @Test
    public void testLoadBalancingClientMultiServersFuture() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuffer> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuffer>(client);
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
        Future<HttpResponse> future = loadBalancingClient.execute(request, null, null);
        assertEquals(EmbeddedResources.defaultPerson, future.get().get(Person.class));
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(good).getTotalRequestsCount());
    }

    
    @Test
    public void testLoadBalancingClientWithRetry() throws Exception {
        RibbonHttpAsyncClient timeoutClient = 
                new RibbonHttpAsyncClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1"));
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuffer> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuffer>(timeoutClient);
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

        loadBalancingClient.execute(request, null, new FullResponseCallback<HttpResponse>() {            
            @Override
            public void completed(HttpResponse response) {
                System.err.println(response.getStatus());
            }
            
            @Override
            public void failed(Throwable e) {
                exception.set(e);
            }

            @Override
            public void cancelled() {
                // TODO Auto-generated method stub
                
            }
        });
        Thread.sleep(10000);
        assertNotNull(exception.get());
        // assertTrue(exception.get().getCause() instanceof io.netty.handler.timeout.ReadTimeoutException);
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(server).getTotalRequestsCount());                
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(server).getSuccessiveConnectionFailureCount());                
    }
    
    @Test
    public void testLoadBalancingClientWithRetryFuture() throws Exception {
        RibbonHttpAsyncClient timeoutClient = 
                new RibbonHttpAsyncClient(DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "1"));
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuffer> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuffer>(timeoutClient);
        loadBalancingClient.setMaxAutoRetries(1);
        loadBalancingClient.setMaxAutoRetriesNextServer(1);
        Server server = new Server("www.microsoft.com:81");
        List<Server> servers = Lists.newArrayList(server);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new AvailabilityFilteringRule());
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        URI uri = new URI("/");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        Future<HttpResponse> future = loadBalancingClient.execute(request, null, null);
        assertNull(future.get());
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(server).getTotalRequestsCount());                
        assertEquals(4, lb.getLoadBalancerStats().getSingleServerStat(server).getSuccessiveConnectionFailureCount());                
    }

    
    @Test
    public void testStream() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testNetty/stream").build();
        final List<String> results = Lists.newArrayList();
        final CountDownLatch latch = new CountDownLatch(1);
        client.execute(request, new SSEDecoder(), new ResponseCallback<HttpResponse, List<String>>() {
            @Override
            public void completed(HttpResponse response) {
                latch.countDown();    
            }

            @Override
            public void failed(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void contentReceived(List<String> element) {
                results.addAll(element);
            }

            @Override
            public void cancelled() {
            }

            @Override
            public void responseReceived(HttpResponse response) {
            }
        });
        latch.await(60, TimeUnit.SECONDS);
        assertEquals(EmbeddedResources.streamContent, results);
    }
    
    @Test
    public void testStreamObservable() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testNetty/stream").build();
        final List<String> results = Lists.newArrayList();
        ObservableAsyncClient<HttpRequest, HttpResponse, ByteBuffer> observableClient = 
                new ObservableAsyncClient<HttpRequest, HttpResponse, ByteBuffer>(client);
        observableClient.stream(request, new SSEDecoder())
            .toBlockingObservable()
            .forEach(new Action1<StreamEvent<HttpResponse, List<String>>>() {

                @Override
                public void call(final StreamEvent<HttpResponse, List<String>> t1) {
                    results.addAll(t1.getEvent());
                }
            });                
        assertEquals(EmbeddedResources.streamContent, results);
    }

    
    @Test
    public void testStreamWithLoadBalancer() throws Exception {
        AsyncLoadBalancingClient<HttpRequest, HttpResponse, ByteBuffer> loadBalancingClient = new AsyncLoadBalancingClient<HttpRequest, 
                HttpResponse, ByteBuffer>(client);
        BaseLoadBalancer lb = new BaseLoadBalancer(new DummyPing(), new RoundRobinRule());
        Server good = new Server("localhost:" + port);
        Server bad = new Server("localhost:" + 33333);
        List<Server> servers = Lists.newArrayList(bad, bad, good);
        lb.setServersList(servers);
        loadBalancingClient.setLoadBalancer(lb);
        loadBalancingClient.setMaxAutoRetriesNextServer(2);

        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testNetty/stream").build();
        final List<String> results = Lists.newArrayList();
        final CountDownLatch latch = new CountDownLatch(1);
        loadBalancingClient.execute(request, new SSEDecoder(), new ResponseCallback<HttpResponse, List<String>>() {
            @Override
            public void completed(HttpResponse response) {
                latch.countDown();    
            }

            @Override
            public void failed(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void contentReceived(List<String> element) {
                results.addAll(element);
            }

            @Override
            public void cancelled() {
            }

            @Override
            public void responseReceived(HttpResponse response) {
            }
        });
        latch.await(60, TimeUnit.SECONDS);
        assertEquals(EmbeddedResources.streamContent, results);
    }

}
