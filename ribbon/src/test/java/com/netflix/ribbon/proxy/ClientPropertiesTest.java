package com.netflix.ribbon.proxy;

import com.netflix.client.config.ClientConfigFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey.Keys;
import com.netflix.config.ConfigurationManager;
import com.netflix.ribbon.DefaultResourceFactory;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.RibbonTransportFactory.DefaultRibbonTransportFactory;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieService;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import org.apache.commons.configuration.Configuration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Allen Wang
 */
public class ClientPropertiesTest {

    private static class MyTransportFactory extends DefaultRibbonTransportFactory {
        private IClientConfig config;

        public MyTransportFactory(ClientConfigFactory clientConfigFactory) {
            super(clientConfigFactory);
        }

        @Override
        public HttpClient<ByteBuf, ByteBuf> newHttpClient(IClientConfig config) {
            this.config = config;
            return super.newHttpClient(config);
        }

        public IClientConfig getClientConfig() {
            return this.config;
        }
    }

    @Test
    public void testAnnotation() {
        MyTransportFactory transportFactory = new MyTransportFactory(ClientConfigFactory.DEFAULT);
        RibbonResourceFactory resourceFactory = new DefaultResourceFactory(ClientConfigFactory.DEFAULT, transportFactory);
        RibbonDynamicProxy.newInstance(SampleMovieService.class, resourceFactory, ClientConfigFactory.DEFAULT, transportFactory);
        Configuration config = ConfigurationManager.getConfigInstance();
        assertEquals("2000", config.getProperty("SampleMovieService.ribbon.ReadTimeout"));
        assertEquals("1000", config.getProperty("SampleMovieService.ribbon.ConnectTimeout"));
        IClientConfig clientConfig = transportFactory.getClientConfig();
        assertEquals(1000, clientConfig.get(Keys.ConnectTimeout).longValue());
        assertEquals(2000, clientConfig.get(Keys.ReadTimeout).longValue());

        config.setProperty("SampleMovieService.ribbon.ReadTimeout", "5000");
        assertEquals(5000, clientConfig.get(Keys.ReadTimeout).longValue());
    }
}
