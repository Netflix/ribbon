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
package com.netflix.ribbon.testutils;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import rx.functions.Func0;

public class TestUtils {
    
    public static void waitUntilTrueOrTimeout(int timeoutMilliseconds, final Func0<Boolean> func) {
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final AtomicBoolean stopThread = new AtomicBoolean(false);
        if (!func.call()) {
            (new Thread() {
                @Override
                public void run() {
                    while (!stopThread.get()) {
                        if (func.call()) {
                            lock.lock();
                            try {
                                condition.signalAll();
                            } finally {
                                lock.unlock();
                            }
                        }
                        try {
                            Thread.sleep(20);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
            lock.lock();
            try {
                condition.await(timeoutMilliseconds, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
                stopThread.set(true);
            }
        }
        assertTrue(func.call());
    }
}
