package com.netflix.client.config;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.common.collect.Sets;

public class CommonClientConfigKeyTest {
    
    @Test
    public void testCommonKeys() {
        IClientConfigKey[] keys = CommonClientConfigKey.values();
        assertTrue(keys.length > 30);
        assertEquals(Sets.newHashSet(keys), CommonClientConfigKey.keys());
        assertTrue(CommonClientConfigKey.keys().contains(CommonClientConfigKey.ConnectTimeout));
    }
}
