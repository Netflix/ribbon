package com.netflix.niws.loadbalancer;


import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import org.easymock.EasyMock;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
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
public class DiscoveryEnabledLoadBalancerSupportsPortOverrideTest {


    @Before
    public void setupMock(){

        PowerMock.mockStatic(DiscoveryManager.class);
        PowerMock.mockStatic(DiscoveryClient.class);

        DiscoveryClient mockedDiscoveryClient = createMock(DiscoveryClient.class);
        DiscoveryManager mockedDiscoveryManager = createMock(DiscoveryManager.class);

        expect(DiscoveryClient.getZone((InstanceInfo) EasyMock.anyObject())).andReturn("dummyZone").anyTimes();
        expect(DiscoveryManager.getInstance()).andReturn(mockedDiscoveryManager).anyTimes();
        expect(mockedDiscoveryManager.getDiscoveryClient()).andReturn(mockedDiscoveryClient).anyTimes();


        expect(mockedDiscoveryClient.getInstancesByVipAddress("dummy", false, "region")).andReturn(getDummyInstanceInfo("dummy", "http://www.host.com", 7000)).anyTimes();
        expect(mockedDiscoveryClient.getInstancesByVipAddress("secureDummy", true, "region")).andReturn(getDummyInstanceInfo("secureDummy", "http://www.host.com", 7001)).anyTimes();

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
        Assert.assertEquals(7000, serverList.get(0).getPort());
    }

    @Test
    public void testDefaultHonorsVipSecurePortDefinition() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition.ribbon.DeploymentContextBasedVipAddresses", "secureDummy");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition.ribbon.IsSecure", "true");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition.ribbon.SecurePort", "6999");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());



        DiscoveryEnabledNIWSServerList deList = new DiscoveryEnabledNIWSServerList();

        DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.class.newInstance();
        clientConfig.loadProperties("DiscoveryEnabled.testDefaultHonorsVipSecurePortDefinition");
        deList.initWithNiwsConfig(clientConfig);

        List<DiscoveryEnabledServer> serverList = deList.getInitialListOfServers();

        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals(7001, serverList.get(0).getPort());
    }

    @Test
    public void testVipPortCanBeOverriden() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.DeploymentContextBasedVipAddresses", "dummy");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.IsSecure", "false");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.Port", "6999");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testVipPortCanBeOverriden.ribbon.ForceClientPortConfiguration", "true");



        DiscoveryEnabledNIWSServerList deList = new DiscoveryEnabledNIWSServerList();

        DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.class.newInstance();
        clientConfig.loadProperties("DiscoveryEnabled.testVipPortCanBeOverriden");
        deList.initWithNiwsConfig(clientConfig);

        List<DiscoveryEnabledServer> serverList = deList.getInitialListOfServers();

        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals(6999, serverList.get(0).getPort());
    }


    @Test
    public void testSecureVipPortCanBeOverriden() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.DeploymentContextBasedVipAddresses", "secureDummy");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.IsSecure", "true");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.SecurePort", "6998");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());

        ConfigurationManager.getConfigInstance().setProperty("DiscoveryEnabled.testSecureVipPortCanBeOverriden.ribbon.ForceClientPortConfiguration", "true");



        DiscoveryEnabledNIWSServerList deList = new DiscoveryEnabledNIWSServerList();

        DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.class.newInstance();
        clientConfig.loadProperties("DiscoveryEnabled.testSecureVipPortCanBeOverriden");
        deList.initWithNiwsConfig(clientConfig);

        List<DiscoveryEnabledServer> serverList = deList.getInitialListOfServers();

        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals(6998, serverList.get(0).getPort());
    }


        protected static List<InstanceInfo> getDummyInstanceInfo(String appName, String host, int port){

            List<InstanceInfo> list = new ArrayList<InstanceInfo>();

            InstanceInfo info = InstanceInfo.Builder.newBuilder().setAppName(appName)
                    .setHostName(host)
                    .setPort(port)
                    .build();

            list.add(info);

            return list;

        }

}
