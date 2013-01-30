/*
*
* Copyright 2013 Netflix, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
package com.netflix.client.config;

import static org.junit.Assert.*;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.ConfigurationManager;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Properties;

/**
 * Test cases to verify the correctness of the Client Configuration settings
 * 
 * @author stonse
 * 
 */
public class ClientConfigTest {



    
    @BeforeClass
    public static void setUp() throws Exception {
    }

    @AfterClass
    public static void shutdown() throws Exception {
    }

    @Test
    public void testNiwsConfigViaProperties() throws Exception {
    	DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
    	DefaultClientConfigImpl override = new DefaultClientConfigImpl();
    	clientConfig.loadDefaultValues();
        Properties props = new Properties();
        
        final String restClientName = "testRestClient";
        
        props.setProperty("netflix.appinfo.stack","xbox");
        props.setProperty("netflix.environment","test");
        
        props.setProperty("appname", "movieservice");
        
        clientConfig.setProperty(props, restClientName, CommonClientConfigKey.AppName.key(), "movieservice");
        clientConfig.setProperty(props, restClientName, CommonClientConfigKey.DeploymentContextBasedVipAddresses.key(),
                "${appname}-${netflix.appinfo.stack}-${netflix.environment},movieservice--${netflix.environment}");
        clientConfig.setProperty(props, restClientName, CommonClientConfigKey.EnableZoneAffinity.key(), "false");
        
        ConfigurationManager.loadProperties(props);
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.ribbon.customProperty", "abc");
        
        clientConfig.loadProperties(restClientName);
        clientConfig.setProperty(CommonClientConfigKey.ConnectTimeout, "1000");
        override.setProperty(CommonClientConfigKey.Port, "8000");
        override.setProperty(CommonClientConfigKey.ConnectTimeout, "5000");
        clientConfig.applyOverride(override);
        
        Assert.assertEquals("movieservice", clientConfig.getProperty(CommonClientConfigKey.AppName));
        Assert.assertEquals("false", clientConfig.getProperty(CommonClientConfigKey.EnableZoneAffinity));        
        Assert.assertEquals("movieservice-xbox-test,movieservice--test", clientConfig.resolveDeploymentContextbasedVipAddresses());
        Assert.assertEquals("5000", clientConfig.getProperty(CommonClientConfigKey.ConnectTimeout));

        Assert.assertEquals("8000", clientConfig.getProperty(CommonClientConfigKey.Port));
        assertEquals("abc", clientConfig.getProperties().get("customProperty"));
        System.out.println("AutoVipAddress:" + clientConfig.resolveDeploymentContextbasedVipAddresses());
        
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.ribbon.EnableZoneAffinity", "true");
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.ribbon.customProperty", "xyz");
        assertEquals("true", clientConfig.getProperty(CommonClientConfigKey.EnableZoneAffinity));
        assertEquals("xyz", clientConfig.getProperties().get("customProperty"));        
    }
    
    @Test
    public void testresolveDeploymentContextbasedVipAddresses() throws Exception {
    	DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
    	clientConfig.loadDefaultValues();
        Properties props = new Properties();
        
        final String restClientName = "testRestClient2";
        
        clientConfig.setProperty(props, restClientName,CommonClientConfigKey.AppName.key(), "movieservice");
        clientConfig.setProperty(props, restClientName, CommonClientConfigKey.DeploymentContextBasedVipAddresses.key(),
                "${<appname>}-${netflix.appinfo.stack}-${netflix.environment}:${<port>},${<appname>}--${netflix.environment}:${<port>}");
        clientConfig.setProperty(props, restClientName, CommonClientConfigKey.Port.key(), "7001");
        clientConfig.setProperty(props, restClientName, CommonClientConfigKey.EnableZoneAffinity.key(), "true");        
        ConfigurationManager.loadProperties(props);
        
        clientConfig.loadProperties(restClientName);
        
        Assert.assertEquals("movieservice", clientConfig.getProperty(CommonClientConfigKey.AppName));
        Assert.assertEquals("true", clientConfig.getProperty(CommonClientConfigKey.EnableZoneAffinity));
        
        ConfigurationManager.getConfigInstance().setProperty("testRestClient2.ribbon.DeploymentContextBasedVipAddresses", "movieservice-xbox-test:7001");
        assertEquals("movieservice-xbox-test:7001", clientConfig.getProperty(CommonClientConfigKey.DeploymentContextBasedVipAddresses));
        
        ConfigurationManager.getConfigInstance().clearProperty("testRestClient2.ribbon.EnableZoneAffinity");
        assertNull(clientConfig.getProperty(CommonClientConfigKey.EnableZoneAffinity));
    }
}

