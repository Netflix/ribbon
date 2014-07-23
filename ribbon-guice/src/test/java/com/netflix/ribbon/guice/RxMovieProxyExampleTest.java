package com.netflix.ribbon.guice;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.config.ConfigurationManager;
import com.netflix.ribbon.examples.rx.RxMovieClientTestBase;
import com.netflix.ribbon.examples.rx.proxy.MovieService;
import com.netflix.ribbon.guice.examples.RxMovieProxyExample;

public class RxMovieProxyExampleTest extends RxMovieClientTestBase {
    
    @Test
    public void shouldBind() {
        ConfigurationManager.getConfigInstance().setProperty(MovieService.class.getName() + ".ribbon.listOfServers", "localhost:" + port);
        
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
