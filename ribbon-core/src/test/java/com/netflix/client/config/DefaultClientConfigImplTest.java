package com.netflix.client.config;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.netflix.config.ConfigurationManager;
import org.junit.rules.TestName;

public class DefaultClientConfigImplTest {
    
    class NewConfigKey<T> extends CommonClientConfigKey<T> {
        protected NewConfigKey(String configKey) {
            super(configKey);
        }
    }

    @Rule
    public TestName testName = new TestName();
    
    @Test
    public void testTypedValue() {
        ConfigurationManager.getConfigInstance().setProperty("myclient.ribbon." + CommonClientConfigKey.ConnectTimeout, "1500");
        DefaultClientConfigImpl config = new DefaultClientConfigImpl();
        config.loadProperties("myclient");
        assertEquals(1500, config.get(CommonClientConfigKey.ConnectTimeout).intValue());
        config.set(CommonClientConfigKey.ConnectTimeout, 2000);
        // The archaius property should override code override
        assertEquals(1500, config.get(CommonClientConfigKey.ConnectTimeout).intValue());
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

    public static class CustomType {
        private final Map<String, String> value;

        public CustomType(Map<String, String> value) {
            this.value = new TreeMap<>(value);
        }

        public static CustomType valueOf(Map<String, String> value) {
            return new CustomType(value);
        }
    }

    final CommonClientConfigKey<CustomType> CustomTypeKey = new CommonClientConfigKey<CustomType>("customMapped", new CustomType(Collections.emptyMap())) {};

    @Test
    public void testMappedProperties() {

        String clientName = testName.getMethodName();

        ConfigurationManager.getConfigInstance().setProperty("ribbon.customMapped.a", "1");
        ConfigurationManager.getConfigInstance().setProperty("ribbon.customMapped.b", "2");
        ConfigurationManager.getConfigInstance().setProperty("ribbon.customMapped.c", "3");
        ConfigurationManager.getConfigInstance().setProperty(clientName + ".ribbon.customMapped.c", "4");
        ConfigurationManager.getConfigInstance().setProperty(clientName + ".ribbon.customMapped.d", "5");
        ConfigurationManager.getConfigInstance().setProperty(clientName + ".ribbon.customMapped.e", "6");

        DefaultClientConfigImpl config = new DefaultClientConfigImpl();
        config.loadProperties(clientName);

        CustomType customType = config.getPrefixMappedProperty(CustomTypeKey).get().get();

        TreeMap<String, String> expected = new TreeMap<>();
        expected.put("a", "1");
        expected.put("b", "2");
        expected.put("c", "4");
        expected.put("d", "5");
        expected.put("e", "6");
        Assert.assertEquals(expected, customType.value);
    }
}
