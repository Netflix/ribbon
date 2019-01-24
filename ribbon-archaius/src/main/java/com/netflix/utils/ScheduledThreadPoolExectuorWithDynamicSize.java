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
 * @deprecated This class is no longer necessary as part of Ribbon and should not be used by anyone
 */
@Deprecated
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
