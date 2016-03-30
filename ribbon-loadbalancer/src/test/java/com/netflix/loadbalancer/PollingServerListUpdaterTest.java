package com.netflix.loadbalancer;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertTrue;

/**
 * @author David Liu
 */
public class PollingServerListUpdaterTest {

    private final long updateIntervalMs = 50;

    @Test
    public void testUpdating() throws Exception {
        PollingServerListUpdater serverListUpdater = new PollingServerListUpdater(0, updateIntervalMs);

        try {
            final AtomicLong lastUpdateTimestamp = new AtomicLong();
            final CountDownLatch countDownLatch = new CountDownLatch(2);
            serverListUpdater.start(new ServerListUpdater.UpdateAction() {
                @Override
                public void doUpdate() {
                    countDownLatch.countDown();
                    lastUpdateTimestamp.set(System.currentTimeMillis());
                }
            });

            assertTrue(countDownLatch.await(2, TimeUnit.SECONDS));
            assertTrue(serverListUpdater.getDurationSinceLastUpdateMs() <= updateIntervalMs);
        } finally {
            serverListUpdater.stop();
        }
    }
}
