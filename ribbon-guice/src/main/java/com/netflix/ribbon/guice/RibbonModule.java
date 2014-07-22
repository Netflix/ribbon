package com.netflix.ribbon.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.netflix.ribbon.ClientConfigFactory;
import com.netflix.ribbon.ClientConfigFactory.DefaultClientConfigFactory;
import com.netflix.ribbon.DefaultHttpRequestTemplateFactory;
import com.netflix.ribbon.DefaultHttpResourceGroupFactory;
import com.netflix.ribbon.HttpResourceGroupFactory;
import com.netflix.ribbon.RibbonTransportFactory;
import com.netflix.ribbon.RibbonTransportFactory.DefaultRibbonTransportFactory;
import com.netflix.ribbon.http.HttpRequestTemplateFactory;

/**
 * Default bindings for Ribbon
 * 
 * @author elandau
 *
 */
public class RibbonModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(HttpResourceGroupFactory.class).to(DefaultHttpResourceGroupFactory.class).in(Scopes.SINGLETON);
        bind(HttpRequestTemplateFactory.class).to(DefaultHttpRequestTemplateFactory.class).in(Scopes.SINGLETON);
        bind(RibbonTransportFactory.class).to(DefaultRibbonTransportFactory.class).in(Scopes.SINGLETON);
        bind(ClientConfigFactory.class).to(DefaultClientConfigFactory.class).in(Scopes.SINGLETON);
    }
}
