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
package com.netflix.client.netty;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import rx.Observer;
import rx.subjects.PublishSubject;
import io.reactivex.netty.client.MaxConnectionsBasedStrategy;
import io.reactivex.netty.client.PoolInsightProvider.PoolStateChangeEvent;
import io.reactivex.netty.client.PoolStats;
import io.reactivex.netty.client.RxClient;

import com.netflix.loadbalancer.Server;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;

public class GlobalPoolStats<T extends RxClient<?, ?>> implements Observer<PoolStateChangeEvent>, PoolStats {

    private final AtomicLong creationCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong reuseCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();
    private final AtomicLong acquireAttemptedCount = new AtomicLong();
    private final AtomicLong acquireSucceededCount = new AtomicLong();
    private final AtomicLong acquireFailedCount = new AtomicLong();
    private final AtomicLong releaseAttemptedCount = new AtomicLong();
    private final AtomicLong releaseSucceededCount = new AtomicLong();
    private final AtomicLong releaseFailedCount = new AtomicLong();

    private final MaxConnectionsBasedStrategy maxConnectionStrategy;
    
    private final Map<Server, T> rxClients;
    private final PublishSubject<PoolStateChangeEvent> subject;
    
    public GlobalPoolStats(String name, MaxConnectionsBasedStrategy maxConnectionStrategy, Map<Server, T> rxClients) {
        Monitors.registerObject(name, this);
        this.rxClients = rxClients;
        this.subject = PublishSubject.create();
        this.maxConnectionStrategy = maxConnectionStrategy;
    }
    
    public PublishSubject<PoolStateChangeEvent> getPublishSubject() {
        return this.subject;
    }
    
    public void onConnectionCreation() {
        creationCount.incrementAndGet();
    }

    public void onConnectFailed() {
        failedCount.incrementAndGet();
    }

    public void onConnectionReuse() {
        reuseCount.incrementAndGet();
    }

    public void onConnectionEviction() {
        evictionCount.incrementAndGet();
    }

    public void onAcquireAttempted() {
        acquireAttemptedCount.incrementAndGet();
    }

    public void onAcquireSucceeded() {
        acquireSucceededCount.incrementAndGet();
    }

    public void onAcquireFailed() {
        acquireFailedCount.incrementAndGet();
    }

    public void onReleaseAttempted() {
        releaseAttemptedCount.incrementAndGet();
    }

    public void onReleaseSucceeded() {
        releaseSucceededCount.incrementAndGet();
    }

    public void onReleaseFailed() {
        releaseFailedCount.incrementAndGet();
    }

    @Monitor(name="AcquireAttempt", type=DataSourceType.COUNTER)
    public long getAcquireAttemptedCount() {
        return acquireAttemptedCount.longValue();
    }

    @Monitor(name="AcquireFailed", type=DataSourceType.COUNTER)
    public long getAcquireFailedCount() {
        return acquireFailedCount.longValue();
    }

    @Monitor(name="AcquireSucceeded", type=DataSourceType.COUNTER)
    public long getAcquireSucceededCount() {
        return acquireSucceededCount.longValue();
    }

    @Monitor(name="Creation", type=DataSourceType.COUNTER)
    public long getCreationCount() {
        return creationCount.longValue();
    }

    @Monitor(name="Deletion", type=DataSourceType.COUNTER)
    public long getEvictionCount() {
        return evictionCount.longValue();
    }

    @Monitor(name="ConnectionFailed", type=DataSourceType.COUNTER)
    public long getFailedCount() {
        return failedCount.longValue();
    }

    @Monitor(name="ReleaseAttempted", type=DataSourceType.COUNTER)
    public long getReleaseAttemptedCount() {
        return releaseAttemptedCount.longValue();
    }

    @Monitor(name="ReleaseFailed", type=DataSourceType.COUNTER)
    public long getReleaseFailedCount() {
        return releaseFailedCount.longValue();
    }

    @Monitor(name="ReleaseSucceeded", type=DataSourceType.COUNTER)
    public long getReleaseSucceededCount() {
        return releaseSucceededCount.longValue();
    }

    @Monitor(name="Reuse", type=DataSourceType.COUNTER)
    public long getReuseCount() {
        return reuseCount.longValue();
    }

    @Monitor(name="InUse", type=DataSourceType.GAUGE)
    public long getInUseCount() {
        long total = 0;
        for (T rxclient: rxClients.values()) {
            total += rxclient.getStats().getInUseCount();
        }
        return total;
    }

    @Monitor(name="Idle", type=DataSourceType.GAUGE)
    public long getIdleCount() {
        long total = 0;
        for (T rxclient: rxClients.values()) {
            total += rxclient.getStats().getIdleCount();
        }
        return total;        
    }

    @Monitor(name="Total", type=DataSourceType.GAUGE)
    public long getTotalConnectionCount() {
        long total = 0;
        for (T rxclient: rxClients.values()) {
            total += rxclient.getStats().getTotalConnectionCount();
        }
        return total;        
    }

    @Monitor(name="PendingAccquire", type=DataSourceType.GAUGE)
    public long getPendingAcquireRequestCount() {
        long total = 0;
        for (T rxclient: rxClients.values()) {
            total += rxclient.getStats().getPendingAcquireRequestCount();
        }
        return total;
    }

    @Monitor(name="PendingRelease", type=DataSourceType.GAUGE)
    public long getPendingReleaseRequestCount() {
        long total = 0;
        for (T rxclient: rxClients.values()) {
            total += rxclient.getStats().getPendingReleaseRequestCount();
        }
        return total;
    }

    @Monitor(name="MaxTotalConnections", type=DataSourceType.GAUGE)
    public int getMaxTotalConnections() {
        return maxConnectionStrategy.getMaxConnections();
    }
    
    @Override
    public void onCompleted() {
        subject.onCompleted();
    }

    @Override
    public void onError(Throwable e) {
        subject.onError(e);
    }

    @Override
    public void onNext(PoolStateChangeEvent stateChangeEvent) {
        subject.onNext(stateChangeEvent);
        switch (stateChangeEvent) {
            case NewConnectionCreated:
                onConnectionCreation();
                break;
            case ConnectFailed:
                onConnectFailed();
                break;
            case OnConnectionReuse:
                onConnectionReuse();
                break;
            case OnConnectionEviction:
                onConnectionEviction();
                break;
            case onAcquireAttempted:
                onAcquireAttempted();
                break;
            case onAcquireSucceeded:
                onAcquireSucceeded();
                break;
            case onAcquireFailed:
                onAcquireFailed();
                break;
            case onReleaseAttempted:
                onReleaseAttempted();
                break;
            case onReleaseSucceeded:
                onReleaseSucceeded();
                break;
            case onReleaseFailed:
                onReleaseFailed();
                break;
        }
    }
}
