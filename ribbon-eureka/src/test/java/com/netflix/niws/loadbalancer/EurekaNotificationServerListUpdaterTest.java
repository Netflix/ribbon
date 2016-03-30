package com.netflix.niws.loadbalancer;

import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.loadbalancer.ServerListUpdater;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Provider;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author David Liu
 */
public class EurekaNotificationServerListUpdaterTest {

    private EurekaClient eurekaClientMock;
    private EurekaClient eurekaClientMock2;

    @Before
    public void setUp() {
        eurekaClientMock = setUpEurekaClientMock();
        eurekaClientMock2 = setUpEurekaClientMock();
    }

    @Test
    public void testUpdating() throws Exception {
        EurekaNotificationServerListUpdater serverListUpdater = new EurekaNotificationServerListUpdater(
                new Provider<EurekaClient>() {
                    @Override
                    public EurekaClient get() {
                        return eurekaClientMock;
                    }
                }
        );

        try {
            Capture<EurekaEventListener> eventListenerCapture = new Capture<EurekaEventListener>();
            eurekaClientMock.registerEventListener(EasyMock.capture(eventListenerCapture));

            EasyMock.replay(eurekaClientMock);

            final CountDownLatch updateCountLatch = new CountDownLatch(2);
            serverListUpdater.start(new ServerListUpdater.UpdateAction() {
                @Override
                public void doUpdate() {
                    updateCountLatch.countDown();
                }
            });

            eventListenerCapture.getValue().onEvent(new CacheRefreshedEvent());
            eventListenerCapture.getValue().onEvent(new CacheRefreshedEvent());

            Assert.assertTrue(updateCountLatch.await(2, TimeUnit.SECONDS));
        } finally {
            serverListUpdater.stop();

            EasyMock.verify(eurekaClientMock);
        }
    }

    @Test
    public void testStopWithCommonExecutor() throws Exception {
        EurekaNotificationServerListUpdater serverListUpdater1 = new EurekaNotificationServerListUpdater(
                new Provider<EurekaClient>() {
                    @Override
                    public EurekaClient get() {
                        return eurekaClientMock;
                    }
                }
        );

        EurekaNotificationServerListUpdater serverListUpdater2 = new EurekaNotificationServerListUpdater(
                new Provider<EurekaClient>() {
                    @Override
                    public EurekaClient get() {
                        return eurekaClientMock2;
                    }
                }
        );

        Capture<EurekaEventListener> eventListenerCapture = new Capture<EurekaEventListener>();
        eurekaClientMock.registerEventListener(EasyMock.capture(eventListenerCapture));

        Capture<EurekaEventListener> eventListenerCapture2 = new Capture<EurekaEventListener>();
        eurekaClientMock2.registerEventListener(EasyMock.capture(eventListenerCapture2));

        EasyMock.replay(eurekaClientMock);
        EasyMock.replay(eurekaClientMock2);

        final CountDownLatch updateCountLatch = new CountDownLatch(2);
        serverListUpdater1.start(new ServerListUpdater.UpdateAction() {
            @Override
            public void doUpdate() {
                updateCountLatch.countDown();
            }
        });

        serverListUpdater2.start(new ServerListUpdater.UpdateAction() {
            @Override
            public void doUpdate() {
                updateCountLatch.countDown();
            }
        });

        eventListenerCapture.getValue().onEvent(new CacheRefreshedEvent());
        eventListenerCapture2.getValue().onEvent(new CacheRefreshedEvent());

        Assert.assertTrue(updateCountLatch.await(2, TimeUnit.SECONDS));  // latch is for both

        serverListUpdater1.stop();
        serverListUpdater2.stop();

        EasyMock.verify(eurekaClientMock);
        EasyMock.verify(eurekaClientMock2);
    }

    private EurekaClient setUpEurekaClientMock() {
        final EurekaClient eurekaClientMock = EasyMock.createMock(EurekaClient.class);

        EasyMock
                .expect(eurekaClientMock.unregisterEventListener(EasyMock.isA(EurekaEventListener.class)))
                .andReturn(true).times(1);

        return eurekaClientMock;
    }
}
