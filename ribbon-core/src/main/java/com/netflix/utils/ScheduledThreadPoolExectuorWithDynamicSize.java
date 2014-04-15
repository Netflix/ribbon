package com.netflix.utils;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import com.netflix.config.DynamicIntProperty;

/**
 * A {@link ScheduledThreadPoolExecutor} whose core size can be dynamically changed by a given {@link DynamicIntProperty} and 
 * registers itself with a shutdown hook to shut down.
 *
 * @author awang
 *
 */
public class ScheduledThreadPoolExectuorWithDynamicSize extends ScheduledThreadPoolExecutor {

    private final Thread shutdownThread;
    
    public ScheduledThreadPoolExectuorWithDynamicSize(final DynamicIntProperty corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize.get(), threadFactory);
        corePoolSize.addCallback(new Runnable() {
            public void run() {
                setCorePoolSize(corePoolSize.get());
            }
        });
        shutdownThread = new Thread(new Runnable() {
            public void run() {
                shutdown();
                if (shutdownThread != null) {
                    try {
                        Runtime.getRuntime().removeShutdownHook(shutdownThread);
                    } catch (IllegalStateException ise) { // NOPMD
                    }
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownThread);
    }
}
