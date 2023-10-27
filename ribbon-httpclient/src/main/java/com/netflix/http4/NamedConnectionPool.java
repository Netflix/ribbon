/*
 *
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.http4;

import java.util.concurrent.TimeUnit;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.patterns.PolledMeter;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.tsccm.BasicPoolEntry;
import org.apache.http.impl.conn.tsccm.ConnPoolByRoute;
import org.apache.http.impl.conn.tsccm.PoolEntryRequest;
import org.apache.http.impl.conn.tsccm.RouteSpecificPool;
import org.apache.http.impl.conn.tsccm.WaitingThreadAborter;
import org.apache.http.params.HttpParams;

import com.google.common.base.Preconditions;

/**
 * A connection pool that provides Servo counters to monitor the efficiency. 
 * Three counters are provided: counter for getting free entries (or reusing entries),
 * counter for creating new entries, and counter for every connection request.
 * 
 * @author awang
 *
 */
public class NamedConnectionPool extends ConnPoolByRoute {

    private Counter freeEntryCounter;
    private Counter createEntryCounter;
    private Counter requestCounter;
    private Counter releaseCounter;
    private Counter deleteCounter;
    private Timer requestTimer;
    private Timer creationTimer;
    private String name;
    
    public NamedConnectionPool(String name, ClientConnectionOperator operator,
            ConnPerRoute connPerRoute, int maxTotalConnections, long connTTL,
            TimeUnit connTTLTimeUnit) {
        super(operator, connPerRoute, maxTotalConnections, connTTL, connTTLTimeUnit);
        initMonitors(name);
    }

    public NamedConnectionPool(String name, ClientConnectionOperator operator,
            ConnPerRoute connPerRoute, int maxTotalConnections) {
        super(operator, connPerRoute, maxTotalConnections);
        initMonitors(name);
    }
    
    public NamedConnectionPool(String name, ClientConnectionOperator operator,
            HttpParams params) {
        super(operator, params);
        initMonitors(name);
    }
    
    NamedConnectionPool(ClientConnectionOperator operator,
            ConnPerRoute connPerRoute, int maxTotalConnections, long connTTL,
            TimeUnit connTTLTimeUnit) {
        super(operator, connPerRoute, maxTotalConnections, connTTL, connTTLTimeUnit);
    }

    NamedConnectionPool(ClientConnectionOperator operator,
            ConnPerRoute connPerRoute, int maxTotalConnections) {
        super(operator, connPerRoute, maxTotalConnections);
    }
    
    NamedConnectionPool(ClientConnectionOperator operator,
            HttpParams params) {
        super(operator, params);
    }
    
    void initMonitors(String name) {
        Preconditions.checkNotNull(name);

        Registry registry = Spectator.globalRegistry();
        PolledMeter.using(registry).withName("connectionCount").monitorValue(this, NamedConnectionPool::getConnectionCount);

        freeEntryCounter = registry.counter(name + "_Reuse");
        createEntryCounter = registry.counter(name + "_CreateNew");
        requestCounter = registry.counter(name + "_Request");
        releaseCounter = registry.counter(name + "_Release");
        deleteCounter = registry.counter(name + "_Delete");
        requestTimer = registry.timer(name + "_RequestConnectionTimer");
        creationTimer = registry.timer(name + "_CreateConnectionTimer");
        this.name = name;
    }

    @Override
    public PoolEntryRequest requestPoolEntry(HttpRoute route, Object state) {
        requestCounter.increment();
        return super.requestPoolEntry(route, state);
    }

    @Override
    protected BasicPoolEntry getFreeEntry(RouteSpecificPool rospl, Object state) {
        BasicPoolEntry entry = super.getFreeEntry(rospl, state);
        if (entry != null) {
            freeEntryCounter.increment();
        }
        return entry;
    }

    @Override
    protected BasicPoolEntry createEntry(RouteSpecificPool rospl,
            ClientConnectionOperator op) {
        createEntryCounter.increment();
        try {
            return creationTimer.recordCallable(() -> super.createEntry(rospl, op));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected BasicPoolEntry getEntryBlocking(HttpRoute route, Object state,
            long timeout, TimeUnit tunit, WaitingThreadAborter aborter)
            throws ConnectionPoolTimeoutException, InterruptedException {
        try {
            return requestTimer.recordCallable(() -> super.getEntryBlocking(route, state, timeout, tunit, aborter));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void freeEntry(BasicPoolEntry entry, boolean reusable,
            long validDuration, TimeUnit timeUnit) {
        releaseCounter.increment();
        super.freeEntry(entry, reusable, validDuration, timeUnit);
    }

    @Override
    protected void deleteEntry(BasicPoolEntry entry) {
        deleteCounter.increment();
        super.deleteEntry(entry);
    }

    public final long getFreeEntryCount() {
        return freeEntryCounter.count();
    }
    
    public final long getCreatedEntryCount() {
        return createEntryCounter.count();
    }
    
    public final long getRequestsCount() {
        return requestCounter.count();
    }   
    
    public final long getReleaseCount() {
        return releaseCounter.count();
    }
    
    public final long getDeleteCount() {
        return deleteCounter.count();
    }
    
    public int getConnectionCount() {
        return this.getConnectionsInPool();
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
    }
}
