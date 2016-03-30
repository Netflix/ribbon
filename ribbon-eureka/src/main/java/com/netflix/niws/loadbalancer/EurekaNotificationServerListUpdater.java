package com.netflix.niws.loadbalancer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaEvent;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.loadbalancer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
        private static final ExecutorService DEFAULT_SERVER_LIST_UPDATE_EXECUTOR = Executors.newCachedThreadPool(
                new ThreadFactoryBuilder()
                        .setNameFormat("EurekaNotificationServerListUpdater-%d")
                        .setDaemon(true)
                        .build()
        );

        private static final Thread SHUTDOWN_THREAD = new Thread(new Runnable() {
            @Override
            public void run() {
                logger.info("Shutting down the Executor for EurekaNotificationServerListUpdater");
                try {
                    DEFAULT_SERVER_LIST_UPDATE_EXECUTOR.shutdown();
                    Runtime.getRuntime().removeShutdownHook(SHUTDOWN_THREAD);
                } catch (Exception e) {
                    // this can happen in the middle of a real shutdown, and that's ok.
                }
            }
        });

        static {
            Runtime.getRuntime().addShutdownHook(SHUTDOWN_THREAD);
        }
    }

    public static ExecutorService getDefaultRefreshExecutor() {
        return LazyHolder.DEFAULT_SERVER_LIST_UPDATE_EXECUTOR;
    }

    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicLong lastUpdated = new AtomicLong(System.currentTimeMillis());
    private final Provider<EurekaClient> eurekaClientProvider;
    private final AtomicReference<EurekaEventListener> updateListenerRef = new AtomicReference<EurekaEventListener>();
    private final ExecutorService refreshExecutor;

    public EurekaNotificationServerListUpdater() {
        this(new LegacyEurekaClientProvider());
    }

    public EurekaNotificationServerListUpdater(final Provider<EurekaClient> eurekaClientProvider) {
        this(eurekaClientProvider, getDefaultRefreshExecutor());
    }

    public EurekaNotificationServerListUpdater(final Provider<EurekaClient> eurekaClientProvider, ExecutorService refreshExecutor) {
        this.eurekaClientProvider = new Provider<EurekaClient>() {
            private volatile EurekaClient eurekaClientInstance;
            @Override
            public synchronized EurekaClient get() {
                if (eurekaClientInstance == null) {
                    eurekaClientInstance = eurekaClientProvider.get();
                }
                return eurekaClientInstance;
            }
        };
        this.refreshExecutor = refreshExecutor;
    }

    @Override
    public synchronized void start(final UpdateAction updateAction) {
        if (isActive.compareAndSet(false, true)) {
            final EurekaEventListener updateListener = new EurekaEventListener() {
                @Override
                public void onEvent(EurekaEvent event) {
                    if (event instanceof CacheRefreshedEvent) {
                        refreshExecutor.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    updateAction.doUpdate();
                                    lastUpdated.set(System.currentTimeMillis());
                                } catch (Exception e) {
                                    logger.warn("Failed to update serverList", e);
                                }
                            }
                        });  // fire and forget
                    }
                }
            };
            updateListenerRef.set(updateListener);
            if (eurekaClientProvider.get() != null) {
                eurekaClientProvider.get().registerEventListener(updateListener);
            }
        } else {
            logger.info("Update listener already registered, no-op");
        }
    }

    @Override
    public synchronized void stop() {
        if (isActive.compareAndSet(true, false)) {
            if (eurekaClientProvider.get() != null) {
                eurekaClientProvider.get().unregisterEventListener(updateListenerRef.get());
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
