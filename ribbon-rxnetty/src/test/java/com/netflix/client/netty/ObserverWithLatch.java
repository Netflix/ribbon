package com.netflix.client.netty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

import rx.Observer;


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
            completed = latch.await(600000, TimeUnit.SECONDS);
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
