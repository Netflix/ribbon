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
package com.netflix.ribbon.examples.rx;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.netflix.client.config.ClientConfigFactory;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.transport.netty.http.LoadBalancingHttpClient;
import com.netflix.config.ConfigurationManager;
import com.netflix.ribbon.DefaultResourceFactory;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.RibbonTransportFactory;
import com.netflix.ribbon.RibbonTransportFactory.DefaultRibbonTransportFactory;
import com.netflix.ribbon.examples.rx.proxy.MovieService;
import com.netflix.ribbon.examples.rx.proxy.RxMovieProxyExample;
import com.netflix.ribbon.guice.RibbonModule;
import com.netflix.ribbon.guice.RibbonResourceProvider;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider.DefaultAnnotationProcessorsProvider;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RxMovieProxyExampleTest extends RxMovieClientTestBase {

    static class MyClientConfigFactory implements ClientConfigFactory {
        @Override
        public IClientConfig newConfig() {
            return new DefaultClientConfigImpl() {
                @Override
                public String getNameSpace() {
                    return "MyConfig";
                }
            };
        }
    }

    @Test
    public void shouldBind() {
        ConfigurationManager.getConfigInstance().setProperty(MovieService.class.getSimpleName() + ".ribbon.listOfServers", "localhost:" + port);
        
        Injector injector = Guice.createInjector(
            new RibbonModule(),
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(MovieService.class).toProvider(new RibbonResourceProvider<MovieService>(MovieService.class)).asEagerSingleton();
                }
            }
            );
        
        RxMovieProxyExample example = injector.getInstance(RxMovieProxyExample.class);
        assertTrue(example.runExample());

    }

    @Test
    public void shouldBindCustomClientConfigFactory() {
        ConfigurationManager.getConfigInstance().setProperty(MovieService.class.getSimpleName() + ".MyConfig.listOfServers", "localhost:" + port);

        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(RibbonResourceFactory.class).to(DefaultResourceFactory.class).in(Scopes.SINGLETON);
                        bind(RibbonTransportFactory.class).to(DefaultRibbonTransportFactory.class).in(Scopes.SINGLETON);
                        bind(AnnotationProcessorsProvider.class).to(DefaultAnnotationProcessorsProvider.class).in(Scopes.SINGLETON);
                        bind(ClientConfigFactory.class).to(MyClientConfigFactory.class).in(Scopes.SINGLETON);
                    }
                },
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(MovieService.class).toProvider(new RibbonResourceProvider<MovieService>(MovieService.class)).asEagerSingleton();
                    }
                }
        );

        RxMovieProxyExample example = injector.getInstance(RxMovieProxyExample.class);
        assertTrue(example.runExample());
    }

    @Test
    public void testTransportFactoryWithInjection() {
        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ClientConfigFactory.class).to(MyClientConfigFactory.class).in(Scopes.SINGLETON);
                        bind(RibbonTransportFactory.class).to(DefaultRibbonTransportFactory.class).in(Scopes.SINGLETON);
                    }
                }
        );

        RibbonTransportFactory transportFactory = injector.getInstance(RibbonTransportFactory.class);
        HttpClient<ByteBuf, ByteBuf> client = transportFactory.newHttpClient("myClient");
        IClientConfig config = ((LoadBalancingHttpClient) client).getClientConfig();
        assertEquals("MyConfig", config.getNameSpace());
    }
}
