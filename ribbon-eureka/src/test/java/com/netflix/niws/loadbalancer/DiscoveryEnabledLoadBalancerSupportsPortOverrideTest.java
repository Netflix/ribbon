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


import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;

import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.replay;
import static org.powermock.api.easymock.PowerMock.verify;


/**
 * Verify behavior of the override flag ForceClientPortConfiguration("ForceClientPortConfiguration")
 *
 * WARNING: This feature should be treated as temporary workaround to support
 * more than 2 ports per server until relevant load balancer paradigms
 * (i.e., Eureka Server/Client) can accommodate more than 2 ports in its metadata.
 *
 * Currently only works with the DiscoveryEnabledNIWSServerList since this is where the current limitation is applicable
 *
 * See also:   https://github.com/Netflix/eureka/issues/71
 *
 * I'll add that testing this is painful due to the DiscoveryManager.getInstance()... singletons man...
 *
 * Created by jzarfoss on 1/7/14.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {DiscoveryManager.class, DiscoveryClient.class} )
@PowerMockIgnore("javax.management.*")
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class DiscoveryEnabledLoadBalancerSupportsPortOverrideTest {


    @Before
    public void setupMock(){

        List<InstanceInfo> dummyII = LoadBalancerTestUtils.getDummyInstanceInfo("dummy", "http://www.host.com", "1.1.1.1", 8001);
        List<InstanceInfo> secureDummyII = LoadBalancerTestUtils.getDummyInstanceInfo("secureDummy", "http://www.host.com", "1.1.1.1", 8002);


        PowerMock.mockStatic(DiscoveryManager.class);
        PowerMock.mockStatic(DiscoveryClient.class);

        DiscoveryClient mockedDiscoveryClient = LoadBalancerTestUtils.mockDiscoveryClient();
        DiscoveryManager mockedDiscoveryManager = createMock(DiscoveryManager.class);

        expect(DiscoveryManager.getInstance()).andReturn(mockedDiscoveryManager).anyTimes();
        expect(mockedDiscoveryManager.getDiscoveryClient()).andReturn(mockedDiscoveryClient).anyTimes();

        expect(mockedDiscoveryClient.getInstancesByVipAddress("dummy", false, "region")).andReturn(dummyII).anyTimes();
        expect(mockedDiscoveryClient.getInstancesByVipAddress("secureDummy", true, "region")).andReturn(secureDummyII).anyTimes();

        replay(DiscoveryManager.class);
        replay(DiscoveryClient.class);
        replay(mockedDiscoveryManager);
        replay(mockedDiscoveryClient);

    }

    @After
    public void afterMock(){
        verify(DiscoveryManager.class);
        verify(DiscoveryClient.class);
    }


    @Test
    public void testDefaultHonorsVipPortDefinition() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipPortDefinition.ribbon.DeploymentContextBasedVipAddresses", "dummy");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipPortDefinition.ribbon.IsSecure", "false");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipPortDefinition.ribbon.Port", "6999");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipPortDefinition.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipPortDefinition.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());



        DiscoveryEnabledNIWSServerList deList = new DiscoveryEnabledNIWSServerList();

        DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.class.newInstance();
        clientConfig.loadProperties("DiscoveryEnabled.testDefaultHonorsVipPortDefinition");
        deList.initWithNiwsConfig(clientConfig);

        List<DiscoveryEnabledServer> serverList = deList.getInitialListOfServers();

        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals(8001, serverList.get(0).getPort());                              // vip indicated
        Assert.assertEquals(8001, serverList.get(0).getInstanceInfo().getPort());            // vip indicated
        Assert.assertEquals(7002, serverList.get(0).getInstanceInfo().getSecurePort());      // 7002 is the secure default
    }

    @Test
    public void testDefaultHonorsVipSecurePortDefinition() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition.ribbon.DeploymentContextBasedVipAddresses", "secureDummy");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition.ribbon.IsSecure", "true");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition.ribbon.SecurePort", "6002");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());



        DiscoveryEnabledNIWSServerList deList = new DiscoveryEnabledNIWSServerList();

        DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.class.newInstance();
        clientConfig.loadProperties("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition");
        deList.initWithNiwsConfig(clientConfig);

        List<DiscoveryEnabledServer> serverList = deList.getInitialListOfServers();

        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals(8002, serverList.get(0).getPort());                         // vip indicated
        Assert.assertEquals(8002, serverList.get(0).getInstanceInfo().getPort());       // vip indicated
        Assert.assertEquals(7002, serverList.get(0).getInstanceInfo().getSecurePort()); // 7002 is the secure default
    }

    @Test
    public void testVipPortCanBeOverriden() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.DeploymentContextBasedVipAddresses", "dummy");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.IsSecure", "false");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.Port", "6001");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.ForceClientPortConfiguration", "true");

        DiscoveryEnabledNIWSServerList deList = new DiscoveryEnabledNIWSServerList();

        DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.class.newInstance();
        clientConfig.loadProperties("DiscoveryEnabled.testVipPortCanBeOverriden");
        deList.initWithNiwsConfig(clientConfig);

        List<DiscoveryEnabledServer> serverList = deList.getInitialListOfServers();

        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals(6001, serverList.get(0).getPort());                           // client property indicated
        Assert.assertEquals(6001, serverList.get(0).getInstanceInfo().getPort());         // client property indicated
        Assert.assertEquals(7002, serverList.get(0).getInstanceInfo().getSecurePort());   // 7002 is the secure default
    }


    @Test
    public void testSecureVipPortCanBeOverriden() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.DeploymentContextBasedVipAddresses", "secureDummy");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.IsSecure", "true");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.SecurePort", "6002");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.ForceClientPortConfiguration", "true");

        DiscoveryEnabledNIWSServerList deList = new DiscoveryEnabledNIWSServerList();

        DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.class.newInstance();
        clientConfig.loadProperties("DiscoveryEnabled.testSecureVipPortCanBeOverriden");
        deList.initWithNiwsConfig(clientConfig);

        List<DiscoveryEnabledServer> serverList = deList.getInitialListOfServers();

        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals(8002, serverList.get(0).getPort());                           // vip indicated
        Assert.assertEquals(8002, serverList.get(0).getInstanceInfo().getPort());         // vip indicated
        Assert.assertEquals(6002, serverList.get(0).getInstanceInfo().getSecurePort());   // client property indicated
    }


    /**
     * Tests case where two different clients want to use the same instance, one with overriden ports and one without
     *
     * @throws Exception for anything unexpected
     */
    @Test
    public void testTwoInstancesDontStepOnEachOther() throws Exception{

        // setup override client

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther1.ribbon.DeploymentContextBasedVipAddresses", "dummy");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther1.ribbon.IsSecure", "false");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther1.ribbon.Port", "6001");  // override from 8001
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther1.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther1.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther1.ribbon.ForceClientPortConfiguration", "true");

        // setup non override client

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther2.ribbon.DeploymentContextBasedVipAddresses", "dummy");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther2.ribbon.IsSecure", "false");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther2.ribbon.Port", "6001");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther2.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther2.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther2.ribbon.ForceClientPortConfiguration", "false");

        // check override client

        DiscoveryEnabledNIWSServerList deList1 = new DiscoveryEnabledNIWSServerList();

        DefaultClientConfigImpl clientConfig1 = DefaultClientConfigImpl.class.newInstance();
        clientConfig1.loadProperties("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther1");
        deList1.initWithNiwsConfig(clientConfig1);

        List<DiscoveryEnabledServer> serverList1 = deList1.getInitialListOfServers();

        Assert.assertEquals(1, serverList1.size());
        Assert.assertEquals(6001, serverList1.get(0).getPort());                           // client property overridden
        Assert.assertEquals(6001, serverList1.get(0).getInstanceInfo().getPort());         // client property overridden
        Assert.assertEquals(7002, serverList1.get(0).getInstanceInfo().getSecurePort());   // 7002 is the secure default

        // check non-override client

        DiscoveryEnabledNIWSServerList deList2 = new DiscoveryEnabledNIWSServerList();

        DefaultClientConfigImpl clientConfig2 = DefaultClientConfigImpl.class.newInstance();
        clientConfig2.loadProperties("DiscoveryEnabled.testTwoInstancesDontStepOnEachOther2");
        deList2.initWithNiwsConfig(clientConfig2);

        List<DiscoveryEnabledServer> serverList2 = deList2.getInitialListOfServers();

        Assert.assertEquals(1, serverList2.size());

        Assert.assertEquals(8001, serverList2.get(0).getPort());                           // client property indicated in ii
        Assert.assertEquals(8001, serverList2.get(0).getInstanceInfo().getPort());         // client property indicated in ii
        Assert.assertEquals(7002, serverList2.get(0).getInstanceInfo().getSecurePort());   // 7002 is the secure default
    }
}
