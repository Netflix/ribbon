package com.netflix.client.netty;

import java.util.Map;

import rx.Observer;
import rx.subjects.PublishSubject;
import io.reactivex.netty.client.MaxConnectionsBasedStrategy;
import io.reactivex.netty.client.PoolInsightProvider.PoolStateChangeEvent;
import io.reactivex.netty.client.PoolStats;
import io.reactivex.netty.client.RxClient;

import com.netflix.loadbalancer.Server;
import io.netty.util.internal.chmv8.LongAdder;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;

public class GlobalPoolStats implements Observer<PoolStateChangeEvent>, PoolStats {

    private final LongAdder creationCount = new LongAdder();
    private final LongAdder failedCount = new LongAdder();
    private final LongAdder reuseCount = new LongAdder();
    private final LongAdder evictionCount = new LongAdder();
    private final LongAdder acquireAttemptedCount = new LongAdder();
    private final LongAdder acquireSucceededCount = new LongAdder();
    private final LongAdder acquireFailedCount = new LongAdder();
    private final LongAdder releaseAttemptedCount = new LongAdder();
    private final LongAdder releaseSucceededCount = new LongAdder();
    private final LongAdder releaseFailedCount = new LongAdder();

    private final MaxConnectionsBasedStrategy maxConnectionStrategy;
    
    private final Map<Server, RxClient> rxClients;
    private final PublishSubject<PoolStateChangeEvent> subject;
    
    public GlobalPoolStats(String name, MaxConnectionsBasedStrategy maxConnectionStrategy, Map<Server, RxClient> rxClients) {
        Monitors.registerObject(name, this);
        this.rxClients = rxClients;
        this.subject = PublishSubject.create();
        this.maxConnectionStrategy = maxConnectionStrategy;
    }
    
    public PublishSubject<PoolStateChangeEvent> getPublishSubject() {
        return this.subject;
    }
    
    public void onConnectionCreation() {
        creationCount.increment();
    }

    public void onConnectFailed() {
        failedCount.increment();
    }

    public void onConnectionReuse() {
        reuseCount.increment();
    }

    public void onConnectionEviction() {
        evictionCount.increment();
    }

    public void onAcquireAttempted() {
        acquireAttemptedCount.increment();
    }

    public void onAcquireSucceeded() {
        acquireSucceededCount.increment();
    }

    public void onAcquireFailed() {
        acquireFailedCount.increment();
    }

    public void onReleaseAttempted() {
        releaseAttemptedCount.increment();
    }

    public void onReleaseSucceeded() {
        releaseSucceededCount.increment();
    }

    public void onReleaseFailed() {
        releaseFailedCount.increment();
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
        for (RxClient rxclient: rxClients.values()) {
            total += rxclient.getStats().getInUseCount();
        }
        return total;
    }

    @Monitor(name="Idle", type=DataSourceType.GAUGE)
    public long getIdleCount() {
        long total = 0;
        for (RxClient rxclient: rxClients.values()) {
            total += rxclient.getStats().getIdleCount();
        }
        return total;        
    }

    @Monitor(name="Total", type=DataSourceType.GAUGE)
    public long getTotalConnectionCount() {
        long total = 0;
        for (RxClient rxclient: rxClients.values()) {
            total += rxclient.getStats().getTotalConnectionCount();
        }
        return total;        
    }

    @Monitor(name="PendingAccquire", type=DataSourceType.GAUGE)
    public long getPendingAcquireRequestCount() {
        long total = 0;
        for (RxClient rxclient: rxClients.values()) {
            total += rxclient.getStats().getPendingAcquireRequestCount();
        }
        return total;
    }

    @Monitor(name="PendingRelease", type=DataSourceType.GAUGE)
    public long getPendingReleaseRequestCount() {
        long total = 0;
        for (RxClient rxclient: rxClients.values()) {
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