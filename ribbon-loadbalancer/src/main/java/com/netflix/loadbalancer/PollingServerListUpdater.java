package com.netflix.loadbalancer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A default strategy for the dynamic server list updater to update.
 * (refactored and moved here from {@link com.netflix.loadbalancer.DynamicServerListLoadBalancer})
 *
 * @author David Liu
 */
public class PollingServerListUpdater implements ServerListUpdater {

    private static final Logger logger = LoggerFactory.getLogger(PollingServerListUpdater.class);

    private static long LISTOFSERVERS_CACHE_UPDATE_DELAY = 1000; // msecs;
    private static int LISTOFSERVERS_CACHE_REPEAT_INTERVAL = 30 * 1000; // msecs;
    private static int POOL_SIZE = 2;

    private static class LazyHolder {
        static ScheduledExecutorService _serverListRefreshExecutor = null;

        static {
            _serverListRefreshExecutor = Executors.newScheduledThreadPool(POOL_SIZE, new ThreadFactoryBuilder()
                    .setNameFormat("PollingServerListUpdater-%d")
                    .setDaemon(true)
                    .build());
        }
    }

    private static ScheduledExecutorService getRefreshExecutor() {
        return LazyHolder._serverListRefreshExecutor;
    }

    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private volatile long lastUpdated = System.currentTimeMillis();
    private final long initialDelayMs;
    private final long refreshIntervalMs;

    private volatile ScheduledFuture<?> scheduledFuture;

    public PollingServerListUpdater() {
        this(LISTOFSERVERS_CACHE_UPDATE_DELAY, LISTOFSERVERS_CACHE_REPEAT_INTERVAL);
    }

    public PollingServerListUpdater(IClientConfig clientConfig) {
        this(LISTOFSERVERS_CACHE_UPDATE_DELAY, getRefreshIntervalMs(clientConfig));
    }

    public PollingServerListUpdater(final long initialDelayMs, final long refreshIntervalMs) {
        this.initialDelayMs = initialDelayMs;
        this.refreshIntervalMs = refreshIntervalMs;
    }

    @Override
    public synchronized void start(final UpdateAction updateAction) {
        if (isActive.compareAndSet(false, true)) {
            final Runnable wrapperRunnable = () ->  {
                if (!isActive.get()) {
                    if (scheduledFuture != null) {
                        scheduledFuture.cancel(true);
                    }
                    return;
                }
                try {
                    updateAction.doUpdate();
                    lastUpdated = System.currentTimeMillis();
                } catch (Exception e) {
                    logger.warn("Failed one update cycle", e);
                }
            };

            scheduledFuture = getRefreshExecutor().scheduleWithFixedDelay(
                    wrapperRunnable,
                    initialDelayMs,
                    refreshIntervalMs,
                    TimeUnit.MILLISECONDS
            );
        } else {
            logger.info("Already active, no-op");
        }
    }

    @Override
    public synchronized void stop() {
        if (isActive.compareAndSet(true, false)) {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
            }
        } else {
            logger.info("Not active, no-op");
        }
    }

    @Override
    public String getLastUpdate() {
        return new Date(lastUpdated).toString();
    }

    @Override
    public long getDurationSinceLastUpdateMs() {
        return System.currentTimeMillis() - lastUpdated;
    }

    @Override
    public int getNumberMissedCycles() {
        if (!isActive.get()) {
            return 0;
        }
        return (int) ((int) (System.currentTimeMillis() - lastUpdated) / refreshIntervalMs);
    }

    @Override
    public int getCoreThreads() {
        return POOL_SIZE;
    }

    private static long getRefreshIntervalMs(IClientConfig clientConfig) {
        return clientConfig.get(CommonClientConfigKey.ServerListRefreshInterval, LISTOFSERVERS_CACHE_REPEAT_INTERVAL);
    }
}
