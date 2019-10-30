/*
*
* Copyright 2014 Netflix, Inc.
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
package com.netflix.niws.loadbalancer;

import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.replay;
import static org.powermock.api.easymock.PowerMock.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.loadbalancer.Server;

/**
 * Verify behavior of the override flag DiscoveryEnabledNIWSServerList.useIpAddr 
 *
 * Currently only works with the DiscoveryEnabledNIWSServerList since this is where the current limitation is applicable
 *
 * See also:   https://groups.google.com/forum/#!topic/eureka_netflix/7M28bK-SCZU
 *
 * Created by aspyker on 8/21/14.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {DiscoveryManager.class, DiscoveryClient.class} )
@PowerMockIgnore("javax.management.*")
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class DiscoveryEnabledLoadBalancerSupportsUseIpAddrTest {
	static final String IP1 = "1.1.1.1";
	static final String HOST1 = "server1.app.host.com";
	static final String IP2 = "1.1.1.2";
	static final String HOST2 = "server1.app.host.com";

    @Before
    public void setupMock(){

        List<InstanceInfo> servers = LoadBalancerTestUtils.getDummyInstanceInfo("dummy", HOST1, IP1, 8080);
        List<InstanceInfo> servers2 = LoadBalancerTestUtils.getDummyInstanceInfo("dummy", HOST2, IP2, 8080);
        servers.addAll(servers2);


        PowerMock.mockStatic(DiscoveryManager.class);
        PowerMock.mockStatic(DiscoveryClient.class);

        DiscoveryClient mockedDiscoveryClient = LoadBalancerTestUtils.mockDiscoveryClient();
        DiscoveryManager mockedDiscoveryManager = createMock(DiscoveryManager.class);

        expect(DiscoveryManager.getInstance()).andReturn(mockedDiscoveryManager).anyTimes();
        expect(mockedDiscoveryManager.getDiscoveryClient()).andReturn(mockedDiscoveryClient).anyTimes();

        expect(mockedDiscoveryClient.getInstancesByVipAddress("dummy", false, "region")).andReturn(servers).anyTimes();

        replay(DiscoveryManager.class);
        replay(DiscoveryClient.class);
        replay(mockedDiscoveryManager);
        replay(mockedDiscoveryClient);
    }

    /**
     * Generic method to help with various tests
     * @param globalspecified if false, will clear property DiscoveryEnabledNIWSServerList.useIpAddr
     * @param global value of DiscoveryEnabledNIWSServerList.useIpAddr
     * @param clientspecified if false, will not set property on client config
     * @param client value of client.namespace.ribbon.UseIPAddrForServer
     */
    private List<Server> testUsesIpAddr(boolean globalSpecified, boolean global, boolean clientSpecified, boolean client) throws Exception{
    	if (globalSpecified) {
            ConfigurationManager.getConfigInstance().setProperty("ribbon.UseIPAddrForServer", global);
    	}
    	else {
    		ConfigurationManager.getConfigInstance().clearProperty("ribbon.UseIPAddrForServer");
    	}
    	if (clientSpecified) {
    		ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testUsesIpAddr.ribbon.UseIPAddrForServer", client);
    	}
    	else {
    		ConfigurationManager.getConfigInstance().clearProperty("DiscoveryEnabled.testUsesIpAddr.ribbon.UseIPAddrForServer");
    	}
    	System.out.println("r = " + ConfigurationManager.getConfigInstance().getProperty("ribbon.UseIPAddrForServer"));
    	System.out.println("d = " + ConfigurationManager.getConfigInstance().getProperty("DiscoveryEnabled.testUsesIpAddr.ribbon.UseIPAddrForServer"));
    	
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testUsesIpAddr.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testUsesIpAddr.ribbon.DeploymentContextBasedVipAddresses", "dummy");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testUsesIpAddr.ribbon.TargetRegion", "region");
        
        DiscoveryEnabledNIWSServerList deList = new DiscoveryEnabledNIWSServerList("TESTVIP:8080");

        DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.class.newInstance();
        clientConfig.loadProperties("DiscoveryEnabled.testUsesIpAddr");
        deList.initWithNiwsConfig(clientConfig);

        List<DiscoveryEnabledServer> serverList = deList.getInitialListOfServers();

        Assert.assertEquals(2, serverList.size());
        List<Server> servers = new ArrayList<Server>();
        for (DiscoveryEnabledServer server : serverList) {
        	servers.add((Server)server);
        }
        return servers;
    }

    /**
     * Test the case where the property has been used specific to client with true
     * @throws Exception
     */
    @Test
    public void testUsesIpAddrByWhenClientTrueOnly() throws Exception{
        List<Server> servers = testUsesIpAddr(false, false, true, true);
        Assert.assertEquals(servers.get(0).getHost(), IP1);
        Assert.assertEquals(servers.get(1).getHost(), IP2);
    }
    
    /**
     * Test the case where the property has been used specific to client with false
     * @throws Exception
     */
    @Test
    public void testUsesIpAddrByWhenClientFalseOnly() throws Exception{
        List<Server> servers = testUsesIpAddr(false, false, true, false);
        Assert.assertEquals(servers.get(0).getHost(), HOST1);
        Assert.assertEquals(servers.get(1).getHost(), HOST2);
    }
    
    @Test
    /**
     * Test the case where the property has been used globally with true
     * @throws Exception
     */
    public void testUsesIpAddrByWhenGlobalTrueOnly() throws Exception{
        List<Server> servers = testUsesIpAddr(true, true, false, false);
        Assert.assertEquals(servers.get(0).getHost(), IP1);
        Assert.assertEquals(servers.get(1).getHost(), IP2);
    }
    
    @Test
    /**
     * Test the case where the property has been used globally with false
     * @throws Exception
     */
    public void testUsesIpAddrByWhenGlobalFalseOnly() throws Exception{
        List<Server> servers = testUsesIpAddr(true, false, false, false);
        Assert.assertEquals(servers.get(0).getHost(), HOST1);
        Assert.assertEquals(servers.get(1).getHost(), HOST2);
    }
    
    @Test
    /**
     * Test the case where the property hasn't been used at the global or client level
     * @throws Exception
     */
    public void testUsesHostnameByDefault() throws Exception{
        List<Server> servers = testUsesIpAddr(false, false, false, false);
        Assert.assertEquals(servers.get(0).getHost(), HOST1);
        Assert.assertEquals(servers.get(1).getHost(), HOST2);
    }
    
    @After
    public void afterMock(){
        verify(DiscoveryManager.class);
        verify(DiscoveryClient.class);
    }
}
