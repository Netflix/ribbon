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
package com.netflix.ribbon.transport.netty.http;

import rx.Observer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.fail;


public class ObserverWithLatch<T> implements Observer<T> {
    public volatile T obj;
    public volatile Throwable error;
    
    private CountDownLatch latch = new CountDownLatch(1);
    public AtomicInteger nextCounter = new AtomicInteger();
    public AtomicInteger errorCounter = new AtomicInteger();

    @Override
    public void onCompleted() {
        latch.countDown();
    }

    @Override
    public void onError(Throwable e) {
        this.error = e;
        errorCounter.incrementAndGet();
        latch.countDown();
    }

    @Override
    public void onNext(T obj) {
        this.obj = obj;
        nextCounter.incrementAndGet();
    }
    
    public void await() {
        boolean completed = false;
        try {
            completed = latch.await(10, TimeUnit.SECONDS);
        } catch (Exception e) { // NOPMD
        }
        if (!completed) {
            fail("Observer not completed");
        }
        if (nextCounter.get() == 0 && errorCounter.get() == 0) {
            fail("onNext() or onError() is not called");
        }
    }
}
