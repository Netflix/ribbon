/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon.transport.netty.http;

import com.google.common.collect.Lists;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.ExecutionContext;
import com.netflix.loadbalancer.reactive.ExecutionInfo;
import com.netflix.loadbalancer.reactive.ExecutionListener;
import com.netflix.loadbalancer.reactive.ExecutionListener.AbortExecutionException;
import com.netflix.ribbon.transport.netty.RibbonTransport;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.junit.Ignore;
import org.junit.Test;
import rx.functions.Action1;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * @author Allen Wang
 */
public class ListenerTest {

    @Test
    public void testFailedExecution() {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues()
                .withProperty(CommonClientConfigKey.ConnectTimeout, "100")
                .withProperty(CommonClientConfigKey.MaxAutoRetries, 1)
                .withProperty(CommonClientConfigKey.MaxAutoRetriesNextServer, 1);

        System.out.println(config);

        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet("/testAsync/person");
        Server badServer  = new Server("localhost:12345");
        Server badServer2 = new Server("localhost:34567");
        List<Server> servers = Lists.newArrayList(badServer, badServer2);

        BaseLoadBalancer lb = LoadBalancerBuilder.<Server>newBuilder()
                .withRule(new AvailabilityFilteringRule())
                .withPing(new DummyPing())
                .buildFixedServerListLoadBalancer(servers);
        
        IClientConfig overrideConfig = DefaultClientConfigImpl.getEmptyConfig();
        TestExecutionListener<ByteBuf, ByteBuf> listener = new TestExecutionListener<ByteBuf, ByteBuf>(request, overrideConfig);
        List<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>> listeners = Lists.<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>>newArrayList(listener);
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(lb, config, new NettyHttpLoadBalancerErrorHandler(config), listeners);
        try {
            client.submit(request, null, overrideConfig).toBlocking().last();
            fail("Exception expected");
        } catch(Exception e) {
            assertNotNull(e);
        }
        assertEquals(1, listener.executionStartCounter.get());
        assertEquals(4, listener.startWithServerCounter.get());
        assertEquals(4, listener.exceptionWithServerCounter.get());
        assertEquals(1, listener.executionFailedCounter.get());
        assertTrue(listener.isContextChecked());
        assertTrue(listener.isCheckExecutionInfo());
        assertNotNull(listener.getFinalThrowable());
        listener.getFinalThrowable().printStackTrace();
        assertTrue(listener.getFinalThrowable() instanceof ClientException);
        assertEquals(100, listener.getContext().getClientProperty(CommonClientConfigKey.ConnectTimeout).intValue());
    }

    @Test
    public void testFailedExecutionForAbsoluteURI() {
        IClientConfig config = DefaultClientConfigImpl
                .getClientConfigWithDefaultValues()
                .withProperty(CommonClientConfigKey.ConnectTimeout, "100")
                .withProperty(CommonClientConfigKey.MaxAutoRetries, 1)
                .withProperty(CommonClientConfigKey.MaxAutoRetriesNextServer, 1);
        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet("http://xyz.unknowhost.xyz/testAsync/person");
        Server badServer = new Server("localhost:12345");
        Server badServer2 = new Server("localhost:34567");
        List<Server> servers = Lists.newArrayList(badServer, badServer2);

        BaseLoadBalancer lb = LoadBalancerBuilder.<Server>newBuilder()
                .withRule(new AvailabilityFilteringRule())
                .withPing(new DummyPing())
                .buildFixedServerListLoadBalancer(servers);
        IClientConfig overrideConfig = DefaultClientConfigImpl.getEmptyConfig();
        TestExecutionListener<ByteBuf, ByteBuf> listener = new TestExecutionListener<ByteBuf, ByteBuf>(request, overrideConfig);
        List<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>> listeners = Lists.<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>>newArrayList(listener);
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(lb, config, new NettyHttpLoadBalancerErrorHandler(config), listeners);
        try {
            client.submit(request, null, overrideConfig).toBlocking().last();
            fail("Exception expected");
        } catch(Exception e) {
            assertNotNull(e);
        }
        assertEquals(1, listener.executionStartCounter.get());
        assertEquals(2, listener.startWithServerCounter.get());
        assertEquals(2, listener.exceptionWithServerCounter.get());
        assertEquals(1, listener.executionFailedCounter.get());
        assertTrue(listener.isContextChecked());
        assertTrue(listener.isCheckExecutionInfo());
        assertTrue(listener.getFinalThrowable() instanceof ClientException);
    }

    @Test
    public void testSuccessExecution() throws IOException {
        MockWebServer server = new MockWebServer();
        String content = "OK";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-type", "application/json")
                .setBody(content));
        server.play();

        IClientConfig config = DefaultClientConfigImpl
                .getClientConfigWithDefaultValues()
                .withProperty(CommonClientConfigKey.ConnectTimeout, "2000")
                .withProperty(CommonClientConfigKey.MaxAutoRetries, 1)
                .withProperty(CommonClientConfigKey.MaxAutoRetriesNextServer, 1);

        System.out.println(config);

        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet("/testAsync/person");
        Server badServer  = new Server("localhost:12345");
        Server goodServer = new Server("localhost:" + server.getPort());
        List<Server> servers = Lists.newArrayList(goodServer, badServer);

        BaseLoadBalancer lb = LoadBalancerBuilder.newBuilder()
                .withRule(new AvailabilityFilteringRule())
                .withPing(new DummyPing())
                .buildFixedServerListLoadBalancer(servers);

        IClientConfig overrideConfig = DefaultClientConfigImpl
                .getEmptyConfig()
                .set(CommonClientConfigKey.ConnectTimeout, 500);

        TestExecutionListener<ByteBuf, ByteBuf> listener = new TestExecutionListener<>(request, overrideConfig);
        List<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>> listeners = Lists.newArrayList(listener);
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(lb, config, new NettyHttpLoadBalancerErrorHandler(config), listeners);
        HttpClientResponse<ByteBuf> response = client.submit(request, null, overrideConfig).toBlocking().last();

        System.out.println(listener);

        assertEquals(200, response.getStatus().code());
        assertEquals(1, listener.executionStartCounter.get());
        assertEquals(3, listener.startWithServerCounter.get());
        assertEquals(2, listener.exceptionWithServerCounter.get());
        assertEquals(0, listener.executionFailedCounter.get());
        assertEquals(1, listener.executionSuccessCounter.get());
        assertEquals(500, listener.getContext().getClientProperty(CommonClientConfigKey.ConnectTimeout).intValue());
        assertTrue(listener.isContextChecked());
        assertTrue(listener.isCheckExecutionInfo());
        assertSame(response, listener.getResponse());
    }

    @Test
    public void testSuccessExecutionOnAbosoluteURI() throws IOException {
        MockWebServer server = new MockWebServer();
        String content = "OK";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "application/json")
                .setBody(content));
        server.play();

        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "2000")
                .withProperty(CommonClientConfigKey.MaxAutoRetries, 1)
                .withProperty(CommonClientConfigKey.MaxAutoRetriesNextServer, 1);
        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet("http://localhost:" + server.getPort() + "/testAsync/person");
        Server badServer = new Server("localhost:12345");
        Server goodServer = new Server("localhost:" + server.getPort());
        List<Server> servers = Lists.newArrayList(goodServer, badServer);

        BaseLoadBalancer lb = LoadBalancerBuilder.<Server>newBuilder()
                .withRule(new AvailabilityFilteringRule())
                .withPing(new DummyPing())
                .buildFixedServerListLoadBalancer(servers);
        IClientConfig overrideConfig = DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.ConnectTimeout, 500);
        TestExecutionListener<ByteBuf, ByteBuf> listener = new TestExecutionListener<ByteBuf, ByteBuf>(request, overrideConfig);
        List<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>> listeners = Lists.<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>>newArrayList(listener);
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(lb, config, new NettyHttpLoadBalancerErrorHandler(config), listeners);
        HttpClientResponse<ByteBuf> response = client.submit(request, null, overrideConfig).toBlocking().last();
        assertEquals(200, response.getStatus().code());
        assertEquals(1, listener.executionStartCounter.get());
        assertEquals(1, listener.startWithServerCounter.get());
        assertEquals(0, listener.exceptionWithServerCounter.get());
        assertEquals(0, listener.executionFailedCounter.get());
        assertEquals(1, listener.executionSuccessCounter.get());
        assertEquals(500, listener.getContext().getClientProperty(CommonClientConfigKey.ConnectTimeout).intValue());
        assertTrue(listener.isContextChecked());
        assertTrue(listener.isCheckExecutionInfo());
        assertSame(response, listener.getResponse());
    }


    @Test
    public void testAbortedExecution() {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "100")
                .withProperty(CommonClientConfigKey.MaxAutoRetries, 1)
                .withProperty(CommonClientConfigKey.MaxAutoRetriesNextServer, 1);
        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet("/testAsync/person");
        Server badServer = new Server("localhost:12345");
        Server badServer2 = new Server("localhost:34567");
        List<Server> servers = Lists.newArrayList(badServer, badServer2);
        BaseLoadBalancer lb = LoadBalancerBuilder.<Server>newBuilder()
                .withRule(new AvailabilityFilteringRule())
                .withPing(new DummyPing())
                .buildFixedServerListLoadBalancer(servers);
        IClientConfig overrideConfig = DefaultClientConfigImpl.getEmptyConfig();
        TestExecutionListener listener = new TestExecutionListener(request, overrideConfig) {
            @Override
            public void onExecutionStart(ExecutionContext context) {
                throw new AbortExecutionException("exit now");
            }
        };
        List<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>> listeners = Lists.<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>>newArrayList(listener);
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(lb, config, new NettyHttpLoadBalancerErrorHandler(config), listeners);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> ref = new AtomicReference<Throwable>();
        client.submit(request, null, overrideConfig).subscribe(new Action1<HttpClientResponse<ByteBuf>>() {
            @Override
            public void call(HttpClientResponse<ByteBuf> byteBufHttpClientResponse) {
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                ref.set(throwable);
                latch.countDown();
            }
        });
        try {
            latch.await(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertTrue(ref.get() instanceof AbortExecutionException);
    }

    @Test
    public void testAbortedExecutionOnServer() {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues().withProperty(CommonClientConfigKey.ConnectTimeout, "100")
                .withProperty(CommonClientConfigKey.MaxAutoRetries, 1)
                .withProperty(CommonClientConfigKey.MaxAutoRetriesNextServer, 1);
        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet("/testAsync/person");
        Server badServer = new Server("localhost:12345");
        Server badServer2 = new Server("localhost:34567");
        List<Server> servers = Lists.newArrayList(badServer, badServer2);
        BaseLoadBalancer lb = LoadBalancerBuilder.<Server>newBuilder()
                .withRule(new AvailabilityFilteringRule())
                .withPing(new DummyPing())
                .buildFixedServerListLoadBalancer(servers);
        IClientConfig overrideConfig = DefaultClientConfigImpl.getEmptyConfig();
        TestExecutionListener listener = new TestExecutionListener(request, overrideConfig) {
            @Override
            public void onStartWithServer(ExecutionContext context, ExecutionInfo info) {
                throw new AbortExecutionException("exit now");
            }
        };
        List<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>> listeners = Lists.<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>>newArrayList(listener);
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(lb, config, new NettyHttpLoadBalancerErrorHandler(config), listeners);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> ref = new AtomicReference<Throwable>();
        client.submit(request, null, overrideConfig).subscribe(new Action1<HttpClientResponse<ByteBuf>>() {
            @Override
            public void call(HttpClientResponse<ByteBuf> byteBufHttpClientResponse) {
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                ref.set(throwable);
                latch.countDown();
            }
        });
        try {
            latch.await(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertTrue(ref.get() instanceof AbortExecutionException);
    }

    @Test
    public void testDisabledListener() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues("myClient").withProperty(CommonClientConfigKey.ConnectTimeout, "2000")
                .withProperty(CommonClientConfigKey.MaxAutoRetries, 1)
                .withProperty(CommonClientConfigKey.MaxAutoRetriesNextServer, 1);
        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet("/testAsync/person");
        Server badServer = new Server("localhost:12345");
        List<Server> servers = Lists.newArrayList(badServer);

        BaseLoadBalancer lb = LoadBalancerBuilder.<Server>newBuilder()
                .withRule(new AvailabilityFilteringRule())
                .withPing(new DummyPing())
                .buildFixedServerListLoadBalancer(servers);
        IClientConfig overrideConfig = DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.ConnectTimeout, 500);
        TestExecutionListener<ByteBuf, ByteBuf> listener = new TestExecutionListener<ByteBuf, ByteBuf>(request, overrideConfig);
        List<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>> listeners = Lists.<ExecutionListener<HttpClientRequest<ByteBuf>, HttpClientResponse<ByteBuf>>>newArrayList(listener);
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(lb, config, new NettyHttpLoadBalancerErrorHandler(config), listeners);
        ConfigurationManager.getConfigInstance().setProperty("ribbon.listener." + TestExecutionListener.class.getName() + ".disabled", "true");
        try {
            client.submit(request, null, overrideConfig).toBlocking().last();
        } catch (Exception e) {
            assertNotNull(e);
        }
        assertEquals(0, listener.executionStartCounter.get());
        assertEquals(0, listener.startWithServerCounter.get());
        assertEquals(0, listener.exceptionWithServerCounter.get());
        assertEquals(0, listener.executionFailedCounter.get());
        assertEquals(0, listener.executionSuccessCounter.get());

        try {
            client.submit(request, null, overrideConfig).toBlocking().last();
        } catch (Exception e) {
            assertNotNull(e);
        }
        assertEquals(0, listener.executionStartCounter.get());
        assertEquals(0, listener.startWithServerCounter.get());
        assertEquals(0, listener.exceptionWithServerCounter.get());
        assertEquals(0, listener.executionFailedCounter.get());
        assertEquals(0, listener.executionSuccessCounter.get());
    }
}
