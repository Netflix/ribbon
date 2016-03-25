package com.netflix.loadbalancer;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.inject.Provider;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author David Liu
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(DiscoveryClient.class)
@PowerMockIgnore("javax.management.*")
public class EurekaDynamicNotificationLoadBalancerTest {

    private final List<InstanceInfo> servers = InstanceInfoGenerator.newBuilder(10, 1).build().toInstanceList();
    private final int initialServerListSize = 4;
    private final int secondServerListSize = servers.size() - initialServerListSize;

    private DefaultClientConfigImpl config;
    private EurekaClient eurekaClientMock;

    @Before
    public void setUp() {
        eurekaClientMock = setUpEurekaClientMock(servers);;

        config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
        String vipAddress = servers.get(0).getVIPAddress();
        config.setProperty(CommonClientConfigKey.DeploymentContextBasedVipAddresses, vipAddress);
    }

    @Test
    public void testEurekaDynamicNotificationLoadBalancer() throws Exception {
        Assert.assertNotEquals("the two test server list counts shoult be different",
                secondServerListSize, initialServerListSize);

        EurekaDynamicNotificationLoadBalancer lb = null;
        try {
            // a whole lotta set up
            Capture<EurekaEventListener> eventListenerCapture = new Capture<EurekaEventListener>();

            eurekaClientMock.registerEventListener(EasyMock.capture(eventListenerCapture));

            PowerMock.replay(DiscoveryClient.class);
            PowerMock.replay(eurekaClientMock);

            // actual testing
            // initial creation and loading of the first serverlist
            lb = new EurekaDynamicNotificationLoadBalancer(
                    config,
                    new Provider<EurekaClient>() {
                        @Override
                        public EurekaClient get() {
                            return eurekaClientMock;
                        }
                    },
                    EurekaDynamicNotificationLoadBalancer.getDefaultServerListUpdateExecutor()
            );

            Assert.assertEquals(initialServerListSize, lb.getServerCount(false));

            // trigger an eureka CacheRefreshEvent
            eventListenerCapture.getValue().onEvent(new CacheRefreshedEvent());

            Assert.assertTrue(verifyFinalServerListCount(secondServerListSize, lb));

        } finally {
            if (lb != null) {
                lb.shutdown();

                PowerMock.verify(DiscoveryClient.class);
                PowerMock.verify(eurekaClientMock);
            }
        }
    }

    @Test
    public void testShutdownMultiple() {
        try {
            eurekaClientMock.registerEventListener(EasyMock.anyObject(EurekaEventListener.class));
            EasyMock.expectLastCall().anyTimes();

            PowerMock.replay(DiscoveryClient.class);
            PowerMock.replay(eurekaClientMock);

            EurekaDynamicNotificationLoadBalancer lb1 = new EurekaDynamicNotificationLoadBalancer(
                    config,
                    new Provider<EurekaClient>() {
                        @Override
                        public EurekaClient get() {
                            return eurekaClientMock;
                        }
                    },
                    EurekaDynamicNotificationLoadBalancer.getDefaultServerListUpdateExecutor()
            );

            EurekaDynamicNotificationLoadBalancer lb2 = new EurekaDynamicNotificationLoadBalancer(
                    config,
                    new Provider<EurekaClient>() {
                        @Override
                        public EurekaClient get() {
                            return eurekaClientMock;
                        }
                    },
                    EurekaDynamicNotificationLoadBalancer.getDefaultServerListUpdateExecutor()
            );

            EurekaDynamicNotificationLoadBalancer lb3 = new EurekaDynamicNotificationLoadBalancer(
                    config,
                    new Provider<EurekaClient>() {
                        @Override
                        public EurekaClient get() {
                            return eurekaClientMock;
                        }
                    },
                    EurekaDynamicNotificationLoadBalancer.getDefaultServerListUpdateExecutor()
            );

            lb3.shutdown();
            lb1.shutdown();
            lb2.shutdown();

            Assert.assertEquals(lb1.getDefaultServerListExecutorReferences(), 0);
        } finally {
            PowerMock.verify(DiscoveryClient.class);
            PowerMock.verify(eurekaClientMock);
        }
    }

    // a hacky thread sleep loop to get around some minor async behaviour
    // max wait time is 2 seconds, but should complete well before that.
    private boolean verifyFinalServerListCount(int finalCount, EurekaDynamicNotificationLoadBalancer lb) throws Exception {
        long stepSize = TimeUnit.MILLISECONDS.convert(50l, TimeUnit.MILLISECONDS);
        long maxTime = TimeUnit.MILLISECONDS.convert(2l, TimeUnit.SECONDS);

        for (int i = 0; i < maxTime; i += stepSize) {
            if (finalCount == lb.getServerCount(false)) {
                return true;
            } else {
                Thread.sleep(stepSize);
            }
        }

        return false;
    }

    private EurekaClient setUpEurekaClientMock(List<InstanceInfo> servers) {
        PowerMock.mockStatic(DiscoveryClient.class);
        EasyMock
                .expect(DiscoveryClient.getZone(EasyMock.isA(InstanceInfo.class)))
                .andReturn("zone").anyTimes();

        final EurekaClient eurekaClientMock = PowerMock.createMock(EurekaClient.class);

        EasyMock
                .expect(eurekaClientMock.getInstancesByVipAddress(EasyMock.anyString(), EasyMock.anyBoolean(), EasyMock.anyString()))
                .andReturn(servers.subList(0, initialServerListSize)).times(1)
                .andReturn(servers.subList(initialServerListSize, servers.size())).anyTimes();

        EasyMock
                .expectLastCall();

        EasyMock
                .expect(eurekaClientMock.unregisterEventListener(EasyMock.isA(EurekaEventListener.class)))
                .andReturn(true).anyTimes();

        return eurekaClientMock;
    }
}
