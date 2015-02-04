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
package com.netflix.ribbon.proxy;

import com.netflix.client.config.ClientConfigFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.RibbonTransportFactory;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieService;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.ClientMetricsEvent;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.junit.Test;
import rx.Observable;
import rx.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/**
 * @author Allen Wang
 */
public class ShutDownTest {

    @Test
    public void testLifeCycleShutdown() throws Exception {
        final AtomicBoolean shutDownCalled = new AtomicBoolean(false);
        final HttpClient<ByteBuf, ByteBuf> client = new HttpClient<ByteBuf, ByteBuf>() {
            @Override
            public Observable<HttpClientResponse<ByteBuf>> submit(HttpClientRequest<ByteBuf> request) {
                return null;
            }

            @Override
            public Observable<HttpClientResponse<ByteBuf>> submit(HttpClientRequest<ByteBuf> request, ClientConfig config) {
                return null;
            }

            @Override
            public Observable<ObservableConnection<HttpClientResponse<ByteBuf>, HttpClientRequest<ByteBuf>>> connect() {
                return null;
            }

            @Override
            public void shutdown() {
                shutDownCalled.set(true);
            }

            @Override
            public String name() {
                return "SampleMovieService";
            }

            @Override
            public Subscription subscribe(MetricEventsListener<? extends ClientMetricsEvent<?>> listener) {
                return null;
            }
        };

        RibbonTransportFactory transportFactory = new RibbonTransportFactory(ClientConfigFactory.DEFAULT) {
            @Override
            public HttpClient<ByteBuf, ByteBuf> newHttpClient(IClientConfig config) {
                return client;
            }
        };
        HttpResourceGroup.Builder groupBuilder = HttpResourceGroup.Builder.newBuilder("SampleMovieService", ClientConfigFactory.DEFAULT, transportFactory);
        HttpResourceGroup group = groupBuilder.build();
        SampleMovieService service = RibbonDynamicProxy.newInstance(SampleMovieService.class, group);
        ProxyLifeCycle proxyLifeCycle = (ProxyLifeCycle) service;
        proxyLifeCycle.shutdown();
        assertTrue(proxyLifeCycle.isShutDown());
        assertTrue(shutDownCalled.get());
    }
}
