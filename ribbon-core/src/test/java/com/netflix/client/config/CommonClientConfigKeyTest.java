package com.netflix.client.config;

import static org.junit.Assert.*;


import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

public class CommonClientConfigKeyTest {
    
    @Test
    public void testCommonKeys() {
        IClientConfigKey[] keys = CommonClientConfigKey.values();
        assertTrue(keys.length > 30);
        assertEquals(new HashSet<>(Arrays.asList(keys)), CommonClientConfigKey.keys());
        assertTrue(CommonClientConfigKey.keys().contains(CommonClientConfigKey.ConnectTimeout));
    }
}
