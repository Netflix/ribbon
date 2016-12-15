package com.netflix.niws.loadbalancer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.config.DynamicIntProperty;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaEvent;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.loadbalancer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A server list updater for the {@link com.netflix.loadbalancer.DynamicServerListLoadBalancer} that
 * utilizes eureka's event listener to trigger LB cache updates.
 *
 * Note that when a cache refreshed notification is received, the actual update on the serverList is
 * done on a separate scheduler as the notification is delivered on an eurekaClient thread.
 *
 * @author David Liu
 */
public class EurekaNotificationServerListUpdater implements ServerListUpdater {

    private static final Logger logger = LoggerFactory.getLogger(EurekaNotificationServerListUpdater.class);

    private static class LazyHolder {
        private final static String CORE_THREAD = "EurekaNotificationServerListUpdater.ThreadPoolSize";
        private final static String QUEUE_SIZE = "EurekaNotificationServerListUpdater.queueSize";
        private final static LazyHolder SINGLETON = new LazyHolder();

        private final DynamicIntProperty poolSizeProp = new DynamicIntProperty(CORE_THREAD, 2);
        private final DynamicIntProperty queueSizeProp = new DynamicIntProperty(QUEUE_SIZE, 1000);
        private final ThreadPoolExecutor defaultServerListUpdateExecutor;
        private final Thread shutdownThread;

        private LazyHolder() {
            int corePoolSize = getCorePoolSize();
            defaultServerListUpdateExecutor = new ThreadPoolExecutor(
                    corePoolSize,
                    corePoolSize * 5,
                    0,
                    TimeUnit.NANOSECONDS,
                    new ArrayBlockingQueue<Runnable>(queueSizeProp.get()),
                    new ThreadFactoryBuilder()
                            .setNameFormat("EurekaNotificationServerListUpdater-%d")
                            .setDaemon(true)
                            .build()
            );

            poolSizeProp.addCallback(new Runnable() {
                @Override
                public void run() {
                    int corePoolSize = getCorePoolSize();
                    defaultServerListUpdateExecutor.setCorePoolSize(corePoolSize);
                    defaultServerListUpdateExecutor.setMaximumPoolSize(corePoolSize * 5);
                }
            });

            shutdownThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    logger.info("Shutting down the Executor for EurekaNotificationServerListUpdater");
                    try {
                        defaultServerListUpdateExecutor.shutdown();
                        Runtime.getRuntime().removeShutdownHook(shutdownThread);
                    } catch (Exception e) {
                        // this can happen in the middle of a real shutdown, and that's ok.
                    }
                }
            });

            Runtime.getRuntime().addShutdownHook(shutdownThread);
        }

        private int getCorePoolSize() {
            int propSize = poolSizeProp.get();
            if (propSize > 0) {
                return propSize;
            }
            return 2; // default
        }        
    }

    public static ExecutorService getDefaultRefreshExecutor() {
        return LazyHolder.SINGLETON.defaultServerListUpdateExecutor;
    }

    /* visible for testing */ final AtomicBoolean updateQueued = new AtomicBoolean(false);
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicLong lastUpdated = new AtomicLong(System.currentTimeMillis());
    private final Provider<EurekaClient> eurekaClientProvider;
    private final ExecutorService refreshExecutor;

    private volatile EurekaEventListener updateListener;
    private volatile EurekaClient eurekaClient;

    public EurekaNotificationServerListUpdater() {
        this(new LegacyEurekaClientProvider());
    }

    public EurekaNotificationServerListUpdater(final Provider<EurekaClient> eurekaClientProvider) {
        this(eurekaClientProvider, getDefaultRefreshExecutor());
    }

    public EurekaNotificationServerListUpdater(final Provider<EurekaClient> eurekaClientProvider, ExecutorService refreshExecutor) {
        this.eurekaClientProvider = eurekaClientProvider;
        this.refreshExecutor = refreshExecutor;
    }

    @Override
    public synchronized void start(final UpdateAction updateAction) {
        if (isActive.compareAndSet(false, true)) {
            this.updateListener = new EurekaEventListener() {
                @Override
                public void onEvent(EurekaEvent event) {
                    if (event instanceof CacheRefreshedEvent) {
                        if (!updateQueued.compareAndSet(false, true)) {  // if an update is already queued
                            logger.info("an update action is already queued, returning as no-op");
                            return;
                        }

                        if (!refreshExecutor.isShutdown()) {
                            try {
                                refreshExecutor.submit(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            updateAction.doUpdate();
                                            lastUpdated.set(System.currentTimeMillis());
                                        } catch (Exception e) {
                                            logger.warn("Failed to update serverList", e);
                                        } finally {
                                            updateQueued.set(false);
                                        }
                                    }
                                });  // fire and forget
                            } catch (Exception e) {
                                logger.warn("Error submitting update task to executor, skipping one round of updates", e);
                                updateQueued.set(false);  // if submit fails, need to reset updateQueued to false
                            }
                        }
                        else {
                            logger.debug("stopping EurekaNotificationServerListUpdater, as refreshExecutor has been shut down");
                            stop();
                        }
                    }
                }
            };
            if (eurekaClient == null) {
                eurekaClient = eurekaClientProvider.get();
            }
            if (eurekaClient != null) {
                eurekaClient.registerEventListener(updateListener);
            } else {
                logger.error("Failed to register an updateListener to eureka client, eureka client is null");
                throw new IllegalStateException("Failed to start the updater, unable to register the update listener due to eureka client being null.");
            }
        } else {
            logger.info("Update listener already registered, no-op");
        }
    }

    @Override
    public synchronized void stop() {
        if (isActive.compareAndSet(true, false)) {
            if (eurekaClient != null) {
                eurekaClient.unregisterEventListener(updateListener);
            }
        } else {
            logger.info("Not currently active, no-op");
        }
    }

    @Override
    public String getLastUpdate() {
        return new Date(lastUpdated.get()).toString();
    }

    @Override
    public long getDurationSinceLastUpdateMs() {
        return System.currentTimeMillis() - lastUpdated.get();
    }

    @Override
    public int getNumberMissedCycles() {
        return 0;
    }

    @Override
    public int getCoreThreads() {
        if (isActive.get()) {
            if (refreshExecutor != null && refreshExecutor instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor) refreshExecutor).getCorePoolSize();
            }
        }
        return 0;
    }
}
