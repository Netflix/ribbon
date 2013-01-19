package com.netflix.niws.client;

import static org.junit.Assert.*;

import com.netflix.config.ConfigurationManager;
import com.netflix.niws.client.NiwsClientConfig.NiwsClientConfigKey;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Properties;

/**
 * Unit Test case for the NIWS Rest Client We use the DiscoveryPing and
 * RoundRobin LoadBalancer for this purpose
 * 
 * @author stonse
 * 
 */
public class NiwsClientConfigTest {



    
    @BeforeClass
    public static void setUp() throws Exception {
    }

    @AfterClass
    public static void shutdown() throws Exception {
    }

    @Test
    public void testNiwsConfigViaProperties() throws Exception {
        NiwsClientConfig clientConfig = NiwsClientConfig.getConfigWithDefaultProperties();
        Properties props = new Properties();
        
        final String restClientName = "testRestClient";
        
        props.setProperty("netflix.appinfo.stack","xbox");
        props.setProperty("netflix.environment","test");
        
        props.setProperty("appname", "movieservice");
        
        NiwsClientConfig.setProperty(props, restClientName, NiwsClientConfigKey.AppName.key(), "movieservice");
        NiwsClientConfig.setProperty(props, restClientName, NiwsClientConfigKey.DeploymentContextBasedVipAddresses.key(),
                "${appname}-${netflix.appinfo.stack}-${netflix.environment},movieservice--${netflix.environment}");
        NiwsClientConfig.setProperty(props, restClientName, NiwsClientConfigKey.EnableZoneAffinity.key(), "false");
        
        ConfigurationManager.loadProperties(props);
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.niws.client.customProperty", "abc");
        
        clientConfig.loadProperties(restClientName);
        
        Assert.assertEquals("movieservice", clientConfig.getProperty(NiwsClientConfigKey.AppName));
        Assert.assertEquals("false", clientConfig.getProperty(NiwsClientConfigKey.EnableZoneAffinity));        
        Assert.assertEquals("movieservice-xbox-test,movieservice--test", clientConfig.resolveDeploymentContextbasedVipAddresses());
        assertEquals("abc", clientConfig.getProperties().get("customProperty"));
        System.out.println("AutoVipAddress:" + clientConfig.resolveDeploymentContextbasedVipAddresses());
        
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.niws.client.EnableZoneAffinity", "true");
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.niws.client.customProperty", "xyz");
        assertEquals("true", clientConfig.getProperty(NiwsClientConfigKey.EnableZoneAffinity));
        assertEquals("xyz", clientConfig.getProperties().get("customProperty"));        
    }
    
    @Test
    public void testresolveDeploymentContextbasedVipAddresses() throws Exception {
        NiwsClientConfig clientConfig = NiwsClientConfig.getConfigWithDefaultProperties();
        Properties props = new Properties();
        
        final String restClientName = "testRestClient2";
        
        NiwsClientConfig.setProperty(props, restClientName,NiwsClientConfigKey.AppName.key(), "movieservice");
        NiwsClientConfig.setProperty(props, restClientName, NiwsClientConfigKey.DeploymentContextBasedVipAddresses.key(),
                "${<appname>}-${netflix.appinfo.stack}-${netflix.environment}:${<port>},${<appname>}--${netflix.environment}:${<port>}");
        NiwsClientConfig.setProperty(props, restClientName, NiwsClientConfigKey.Port.key(), "7001");
        NiwsClientConfig.setProperty(props, restClientName, NiwsClientConfigKey.EnableZoneAffinity.key(), "true");        
        ConfigurationManager.loadProperties(props);
        
        clientConfig.loadProperties(restClientName);
        
        Assert.assertEquals("movieservice", clientConfig.getProperty(NiwsClientConfigKey.AppName));
        Assert.assertEquals("true", clientConfig.getProperty(NiwsClientConfigKey.EnableZoneAffinity));
        
        ConfigurationManager.getConfigInstance().setProperty("testRestClient2.niws.client.DeploymentContextBasedVipAddresses", "movieservice-xbox-test:7001");
        assertEquals("movieservice-xbox-test:7001", clientConfig.getProperty(NiwsClientConfigKey.DeploymentContextBasedVipAddresses));
        
        ConfigurationManager.getConfigInstance().clearProperty("testRestClient2.niws.client.EnableZoneAffinity");
        assertNull(clientConfig.getProperty(NiwsClientConfigKey.EnableZoneAffinity));
    }
}

