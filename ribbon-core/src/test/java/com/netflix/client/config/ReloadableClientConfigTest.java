package com.netflix.client.config;

import com.netflix.config.ConfigurationManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class ReloadableClientConfigTest {
    @Rule
    public TestName testName = new TestName();

    private CommonClientConfigKey<Integer> testKey;

    @Before
    public void before() {
        this.testKey = new CommonClientConfigKey<Integer>(testName.getMethodName(), -1) {};
    }

    @Test
    public void testOverrideLoadedConfig() {
        final DefaultClientConfigImpl overrideconfig = new DefaultClientConfigImpl();
        overrideconfig.set(testKey, 123);

        final DefaultClientConfigImpl config = new DefaultClientConfigImpl();
        config.loadDefaultValues();
        config.applyOverride(overrideconfig);

        Assert.assertEquals(123, config.get(testKey).intValue());
    }

    @Test
    public void setBeforeLoading() {
        ConfigurationManager.getConfigInstance().setProperty("ribbon." + testKey.key(), "123");

        final DefaultClientConfigImpl config = new DefaultClientConfigImpl();
        config.loadProperties("foo");

        Assert.assertEquals(123, config.get(testKey).intValue());
    }

    @Test
    public void setAfterLoading() {
        final DefaultClientConfigImpl config = new DefaultClientConfigImpl();
        config.loadProperties("foo");
        config.set(testKey, 456);

        ConfigurationManager.getConfigInstance().setProperty("ribbon." + testKey.key(), "123");

        Assert.assertEquals(123, config.get(testKey).intValue());
    }
}
