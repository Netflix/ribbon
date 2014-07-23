package com.netflix.ribbon.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.netflix.client.config.ClientConfigFactory;
import com.netflix.client.config.ClientConfigFactory.DefaultClientConfigFactory;
import com.netflix.ribbon.DefaultHttpResourceGroupFactory;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.RibbonTransportFactory;
import com.netflix.ribbon.RibbonTransportFactory.DefaultRibbonTransportFactory;

/**
 * Default bindings for Ribbon
 * 
 * @author elandau
 *
 */
public class RibbonModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RibbonResourceFactory.class).to(DefaultHttpResourceGroupFactory.class).in(Scopes.SINGLETON);
        bind(RibbonTransportFactory.class).to(DefaultRibbonTransportFactory.class).in(Scopes.SINGLETON);
        bind(ClientConfigFactory.class).to(DefaultClientConfigFactory.class).in(Scopes.SINGLETON);
    }
}
