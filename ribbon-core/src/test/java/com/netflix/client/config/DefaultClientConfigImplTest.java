package com.netflix.client.config;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

import com.netflix.config.ConfigurationManager;

public class DefaultClientConfigImplTest {
    
    class NewConfigKey<T> extends CommonClientConfigKey<T> {
        protected NewConfigKey(String configKey) {
            super(configKey);
        }
    }
    
    @Test
    public void testTypedValue() {
        ConfigurationManager.getConfigInstance().setProperty("myclient.ribbon." + CommonClientConfigKey.ConnectTimeout, "1000");    
        DefaultClientConfigImpl config = new DefaultClientConfigImpl();
        config.loadProperties("myclient");
        assertEquals("1000", config.getProperty(CommonClientConfigKey.ConnectTimeout));
        assertEquals(1000, config.get(CommonClientConfigKey.ConnectTimeout).intValue());
        config.set(CommonClientConfigKey.ConnectTimeout, 2000);
        // The archaius property should override code override
        assertEquals(1000, config.get(CommonClientConfigKey.ConnectTimeout).intValue());
    }

    @Test
    public void shouldThrowExceptionForIncorrectProperties() {
        ConfigurationManager.getConfigInstance().setProperty("myclient.ribbon", "bar");
        DefaultClientConfigImpl config = new DefaultClientConfigImpl();
        String message = "";
        try {
            config.loadProperties("myclient");
        } catch (RuntimeException ex) {
            message = ex.getMessage();
        }
        assertEquals("Property ribbon is invalid", message);
    }
    
    @Test
    public void testNewType() {
        CommonClientConfigKey<Date> key = new CommonClientConfigKey<Date>("date") {};
        assertEquals(Date.class, key.type());
    }
    
    @Test
    public void testSubClass() {
        NewConfigKey<Date> key = new NewConfigKey<Date>("date") {};
        assertEquals(Date.class, key.type());        
    }
}
