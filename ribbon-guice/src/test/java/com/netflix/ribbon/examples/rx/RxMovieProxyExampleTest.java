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
import com.netflix.config.ConfigurationManager;
import com.netflix.ribbon.examples.rx.proxy.MovieService;
import com.netflix.ribbon.examples.rx.proxy.RxMovieProxyExample;
import com.netflix.ribbon.guice.RibbonModule;
import com.netflix.ribbon.guice.RibbonResourceProvider;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RxMovieProxyExampleTest extends RxMovieClientTestBase {
    
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
}
