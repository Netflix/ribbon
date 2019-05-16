package com.netflix.client.config;

import com.netflix.config.ConfigurationManager;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.Map;
import java.util.TreeMap;

public class ArchaiusPropertyResolverTest {
    @Rule
    public TestName testName = new TestName();

    @Test
    public void mapFromPrefixedKeys() {
        final String prefix = "client.ribbon." + testName.getMethodName();

        final AbstractConfiguration config = ConfigurationManager.getConfigInstance();

        config.setProperty(prefix + ".a", "1");
        config.setProperty(prefix + ".b", "2");
        config.setProperty(prefix + ".c", "3");

        final ArchaiusPropertyResolver resolver = ArchaiusPropertyResolver.INSTANCE;

        final Map<String, String> map = new TreeMap<>();

        resolver.forEach(prefix, map::put);

        final Map<String, String> expected = new TreeMap<>();
        expected.put("a", "1");
        expected.put("b", "2");
        expected.put("c", "3");

        Assert.assertEquals(expected, map);
    }

    @Test
    public void noCallbackIfNoValues() {
        final String prefix = "client.ribbon." + testName.getMethodName();

        final ArchaiusPropertyResolver resolver = ArchaiusPropertyResolver.INSTANCE;

        final Map<String, String> map = new TreeMap<>();

        resolver.forEach(prefix, map::put);

        Assert.assertTrue(map.toString(), map.isEmpty());
    }
}
