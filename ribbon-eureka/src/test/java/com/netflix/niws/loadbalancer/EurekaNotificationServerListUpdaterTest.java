package com.netflix.niws.loadbalancer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author David Liu
 */
public class EurekaNotificationServerListUpdaterTest {

    private EurekaClient eurekaClientMock;
    private EurekaClient eurekaClientMock2;

    private ThreadPoolExecutor testExecutor;

    @Before
    public void setUp() {
        eurekaClientMock = setUpEurekaClientMock();
        eurekaClientMock2 = setUpEurekaClientMock();

        // use a test executor so that the tests do not share executors
        testExecutor = new ThreadPoolExecutor(
                2,
                2 * 5,
                0,
                TimeUnit.NANOSECONDS,
                new ArrayBlockingQueue<Runnable>(1000),
                new ThreadFactoryBuilder()
                        .setNameFormat("EurekaNotificationServerListUpdater-%d")
                        .setDaemon(true)
                        .build()
        );
    }

    @Test
    public void testUpdating() throws Exception {
        EurekaNotificationServerListUpdater serverListUpdater = new EurekaNotificationServerListUpdater(
                new Provider<EurekaClient>() {
                    @Override
                    public EurekaClient get() {
                        return eurekaClientMock;
                    }
                },
                testExecutor
        );

        try {
            Capture<EurekaEventListener> eventListenerCapture = new Capture<EurekaEventListener>();
            eurekaClientMock.registerEventListener(EasyMock.capture(eventListenerCapture));

            EasyMock.replay(eurekaClientMock);

            final AtomicBoolean firstTime = new AtomicBoolean(false);
            final CountDownLatch firstLatch = new CountDownLatch(1);
            final CountDownLatch secondLatch = new CountDownLatch(1);
            serverListUpdater.start(new ServerListUpdater.UpdateAction() {
                @Override
                public void doUpdate() {
                    if (firstTime.compareAndSet(false, true)) {
                        firstLatch.countDown();
                    } else {
                        secondLatch.countDown();
                    }
                }
            });

            eventListenerCapture.getValue().onEvent(new CacheRefreshedEvent());
            Assert.assertTrue(firstLatch.await(2, TimeUnit.SECONDS));
            // wait a bit for the updateQueued flag to be reset
            for (int i = 1; i < 10; i++) {
                if (serverListUpdater.updateQueued.get()) {
                    Thread.sleep(i * 100);
                } else {
                    break;
                }
            }

            eventListenerCapture.getValue().onEvent(new CacheRefreshedEvent());
            Assert.assertTrue(secondLatch.await(2, TimeUnit.SECONDS));
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
                },
                testExecutor
        );

        EurekaNotificationServerListUpdater serverListUpdater2 = new EurekaNotificationServerListUpdater(
                new Provider<EurekaClient>() {
                    @Override
                    public EurekaClient get() {
                        return eurekaClientMock2;
                    }
                },
                testExecutor
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

    @Test
    public void testTaskAlreadyQueued() throws Exception {
        EurekaNotificationServerListUpdater serverListUpdater = new EurekaNotificationServerListUpdater(
                new Provider<EurekaClient>() {
                    @Override
                    public EurekaClient get() {
                        return eurekaClientMock;
                    }
                },
                testExecutor
        );

        try {
            Capture<EurekaEventListener> eventListenerCapture = new Capture<EurekaEventListener>();
            eurekaClientMock.registerEventListener(EasyMock.capture(eventListenerCapture));

            EasyMock.replay(eurekaClientMock);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            serverListUpdater.start(new ServerListUpdater.UpdateAction() {
                @Override
                public void doUpdate() {
                    if (countDownLatch.getCount() == 0) {
                        Assert.fail("should only countdown once");
                    }
                    countDownLatch.countDown();
                }
            });

            eventListenerCapture.getValue().onEvent(new CacheRefreshedEvent());
            eventListenerCapture.getValue().onEvent(new CacheRefreshedEvent());

            Assert.assertTrue(countDownLatch.await(2, TimeUnit.SECONDS));
            Thread.sleep(100);  // sleep a bit more

            Assert.assertFalse(serverListUpdater.updateQueued.get());
        } finally {
            serverListUpdater.stop();

            EasyMock.verify(eurekaClientMock);
        }
    }

    @Test
    public void testSubmitExceptionClearQueued() {
        ThreadPoolExecutor executorMock = EasyMock.createMock(ThreadPoolExecutor.class);
        EasyMock.expect(executorMock.submit(EasyMock.isA(Runnable.class)))
                .andThrow(new RejectedExecutionException("test exception"));
        EasyMock.expect(executorMock.isShutdown()).andReturn(Boolean.FALSE);
        EurekaNotificationServerListUpdater serverListUpdater = new EurekaNotificationServerListUpdater(
                new Provider<EurekaClient>() {
                    @Override
                    public EurekaClient get() {
                        return eurekaClientMock;
                    }
                },
                executorMock
        );

        try {
            Capture<EurekaEventListener> eventListenerCapture = new Capture<EurekaEventListener>();
            eurekaClientMock.registerEventListener(EasyMock.capture(eventListenerCapture));

            EasyMock.replay(eurekaClientMock);
            EasyMock.replay(executorMock);

            serverListUpdater.start(new ServerListUpdater.UpdateAction() {
                @Override
                public void doUpdate() {
                    Assert.fail("should not reach here");
                }
            });

            eventListenerCapture.getValue().onEvent(new CacheRefreshedEvent());

            Assert.assertFalse(serverListUpdater.updateQueued.get());
        } finally {
            serverListUpdater.stop();

            EasyMock.verify(executorMock);
            EasyMock.verify(eurekaClientMock);
        }

    }
    
    @Test
    public void testEurekaClientUnregister() {
        ThreadPoolExecutor executorMock = EasyMock.createMock(ThreadPoolExecutor.class);
        EasyMock.expect(executorMock.isShutdown()).andReturn(Boolean.TRUE);

        EurekaNotificationServerListUpdater serverListUpdater = new EurekaNotificationServerListUpdater(
                new Provider<EurekaClient>() {
                    @Override
                    public EurekaClient get() {
                        return eurekaClientMock;
                    }
                },
                executorMock
        );

        try {
            Capture<EurekaEventListener> registeredListener = new Capture<EurekaEventListener>();
            eurekaClientMock.registerEventListener(EasyMock.capture(registeredListener));

            EasyMock.replay(eurekaClientMock);
            EasyMock.replay(executorMock);

            serverListUpdater.start(new ServerListUpdater.UpdateAction() {
                @Override
                public void doUpdate() {
                    Assert.fail("should not reach here");
                }
            });

            registeredListener.getValue().onEvent(new CacheRefreshedEvent());
            
        } finally {
            EasyMock.verify(executorMock);
            EasyMock.verify(eurekaClientMock);
        }

    }

    @Test(expected = IllegalStateException.class)
    public void testFailIfDiscoveryIsNotAvailable() {
        EurekaNotificationServerListUpdater serverListUpdater = new EurekaNotificationServerListUpdater(
                new Provider<EurekaClient>() {
                    @Override
                    public EurekaClient get() {
                        return null;
                    }
                },
                testExecutor
        );

        serverListUpdater.start(new ServerListUpdater.UpdateAction() {
            @Override
            public void doUpdate() {
                Assert.fail("Should not reach here");
            }
        });
    }

    private EurekaClient setUpEurekaClientMock() {
        final EurekaClient eurekaClientMock = EasyMock.createMock(EurekaClient.class);

        EasyMock
                .expect(eurekaClientMock.unregisterEventListener(EasyMock.isA(EurekaEventListener.class)))
                .andReturn(true).times(1);

        return eurekaClientMock;
    }
}
