/*s
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
 * Test cases to verify the correctness of the Client Configuration settings
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
        IClientConfig clientConfig = NiwsClientConfig.getConfigWithDefaultProperties();
        Properties props = new Properties();
        
        final String restClientName = "testRestClient";
        
        props.setProperty("netflix.appinfo.stack","xbox");
        props.setProperty("netflix.environment","test");
        
        props.setProperty("appname", "movieservice");
        
        NiwsClientConfig.setProperty(props, restClientName, CommonClientConfigKey.AppName.key(), "movieservice");
        NiwsClientConfig.setProperty(props, restClientName, CommonClientConfigKey.DeploymentContextBasedVipAddresses.key(),
                "${appname}-${netflix.appinfo.stack}-${netflix.environment},movieservice--${netflix.environment}");
        NiwsClientConfig.setProperty(props, restClientName, CommonClientConfigKey.EnableZoneAffinity.key(), "false");
        
        ConfigurationManager.loadProperties(props);
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.niws.client.customProperty", "abc");
        
        clientConfig.loadProperties(restClientName);
        
        Assert.assertEquals("movieservice", clientConfig.getProperty(CommonClientConfigKey.AppName));
        Assert.assertEquals("false", clientConfig.getProperty(CommonClientConfigKey.EnableZoneAffinity));        
        Assert.assertEquals("movieservice-xbox-test,movieservice--test", clientConfig.resolveDeploymentContextbasedVipAddresses());
        assertEquals("abc", clientConfig.getProperties().get("customProperty"));
        System.out.println("AutoVipAddress:" + clientConfig.resolveDeploymentContextbasedVipAddresses());
        
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.niws.client.EnableZoneAffinity", "true");
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.niws.client.customProperty", "xyz");
        assertEquals("true", clientConfig.getProperty(CommonClientConfigKey.EnableZoneAffinity));
        assertEquals("xyz", clientConfig.getProperties().get("customProperty"));        
    }
    
    @Test
    public void testresolveDeploymentContextbasedVipAddresses() throws Exception {
        IClientConfig clientConfig = NiwsClientConfig.getConfigWithDefaultProperties();
        Properties props = new Properties();
        
        final String restClientName = "testRestClient2";
        
        NiwsClientConfig.setProperty(props, restClientName,CommonClientConfigKey.AppName.key(), "movieservice");
        NiwsClientConfig.setProperty(props, restClientName, CommonClientConfigKey.DeploymentContextBasedVipAddresses.key(),
                "${<appname>}-${netflix.appinfo.stack}-${netflix.environment}:${<port>},${<appname>}--${netflix.environment}:${<port>}");
        NiwsClientConfig.setProperty(props, restClientName, CommonClientConfigKey.Port.key(), "7001");
        NiwsClientConfig.setProperty(props, restClientName, CommonClientConfigKey.EnableZoneAffinity.key(), "true");        
        ConfigurationManager.loadProperties(props);
        
        clientConfig.loadProperties(restClientName);
        
        Assert.assertEquals("movieservice", clientConfig.getProperty(CommonClientConfigKey.AppName));
        Assert.assertEquals("true", clientConfig.getProperty(CommonClientConfigKey.EnableZoneAffinity));
        
        ConfigurationManager.getConfigInstance().setProperty("testRestClient2.niws.client.DeploymentContextBasedVipAddresses", "movieservice-xbox-test:7001");
        assertEquals("movieservice-xbox-test:7001", clientConfig.getProperty(CommonClientConfigKey.DeploymentContextBasedVipAddresses));
        
        ConfigurationManager.getConfigInstance().clearProperty("testRestClient2.niws.client.EnableZoneAffinity");
        assertNull(clientConfig.getProperty(CommonClientConfigKey.EnableZoneAffinity));
    }
}

