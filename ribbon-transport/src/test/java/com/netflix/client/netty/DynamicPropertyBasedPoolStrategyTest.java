package com.netflix.client.netty;

import static org.junit.Assert.*;

import org.junit.Test;

import com.netflix.config.ConfigurationManager;

public class DynamicPropertyBasedPoolStrategyTest {
    @Test
    public void testResize() {
        ConfigurationManager.getConfigInstance().setProperty("foo", "150");
        DynamicPropertyBasedPoolStrategy strategy = new DynamicPropertyBasedPoolStrategy(100, "foo");
        assertEquals(150, strategy.getMaxConnections());
        ConfigurationManager.getConfigInstance().setProperty("foo", "200");
        assertEquals(200, strategy.getMaxConnections());
        ConfigurationManager.getConfigInstance().setProperty("foo", "50");
        assertEquals(50, strategy.getMaxConnections());
    }

}
