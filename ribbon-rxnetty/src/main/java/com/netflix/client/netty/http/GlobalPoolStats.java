package com.netflix.client.netty.http;

import rx.Observer;
import io.reactivex.netty.client.PoolInsightProvider;
import io.reactivex.netty.protocol.http.client.HttpClient;

import com.netflix.numerus.LongAdder;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;

public class GlobalPoolStats implements Observer<PoolInsightProvider.PoolStateChangeEvent> {

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

    private final NettyHttpClient client;
    
    public GlobalPoolStats(String name, NettyHttpClient client) {
        Monitors.registerObject(name, this);
        this.client = client;
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
        System.err.println("onConnectionEviction");
        evictionCount.increment();
    }

    public void onAcquireAttempted() {
        System.err.println("onAcquireAttempted");
        acquireAttemptedCount.increment();
    }

    public void onAcquireSucceeded() {
        System.err.println("onAcquireSucceeded");
        acquireSucceededCount.increment();
    }

    public void onAcquireFailed() {
        acquireFailedCount.increment();
    }

    public void onReleaseAttempted() {
        System.err.println("onReleaseAttempted");
        releaseAttemptedCount.increment();
    }

    public void onReleaseSucceeded() {
        System.err.println("onReleaseSucceeded");
        releaseSucceededCount.increment();
    }

    public void onReleaseFailed() {
        System.err.println("onReleaseAttempted");
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
        for (HttpClient<?, ?> rxclient: client.getCurrentHttpClients().values()) {
            total += rxclient.getStats().getInUseCount();
        }
        return total;
    }

    @Monitor(name="Idle", type=DataSourceType.GAUGE)
    public long getIdleCount() {
        long total = 0;
        for (HttpClient<?, ?> rxclient: client.getCurrentHttpClients().values()) {
            total += rxclient.getStats().getIdleCount();
        }
        return total;        
    }

    @Monitor(name="Total", type=DataSourceType.GAUGE)
    public long getTotalConnectionCount() {
        long total = 0;
        for (HttpClient<?, ?> rxclient: client.getCurrentHttpClients().values()) {
            total += rxclient.getStats().getTotalConnectionCount();
        }
        return total;        
    }

    @Monitor(name="PendingAccquire", type=DataSourceType.GAUGE)
    public long getPendingAcquireRequestCount() {
        long total = 0;
        for (HttpClient<?, ?> rxclient: client.getCurrentHttpClients().values()) {
            total += rxclient.getStats().getPendingAcquireRequestCount();
        }
        return total;
    }

    @Monitor(name="PendingRelease", type=DataSourceType.GAUGE)
    public long getPendingReleaseRequestCount() {
        long total = 0;
        for (HttpClient<?, ?> rxclient: client.getCurrentHttpClients().values()) {
            total += rxclient.getStats().getPendingReleaseRequestCount();
        }
        return total;
    }

    @Monitor(name="MaxTotalConnections", type=DataSourceType.GAUGE)
    public int getMaxTotalConnections() {
        return client.getMaxTotalConnections();
    }

    @Override
    public void onCompleted() {
    }

    @Override
    public void onError(Throwable e) {
    }


    @Override
    public void onNext(PoolInsightProvider.PoolStateChangeEvent stateChangeEvent) {
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
