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

import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.tsccm.BasicPoolEntry;
import org.apache.http.impl.conn.tsccm.ConnPoolByRoute;
import org.apache.http.impl.conn.tsccm.PoolEntryRequest;
import org.apache.http.impl.conn.tsccm.RouteSpecificPool;
import org.apache.http.params.HttpParams;

import com.google.common.base.Preconditions;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;

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
        freeEntryCounter = Monitors.newCounter(name + "_ReuseEntry");
        createEntryCounter = Monitors.newCounter(name + "_CreatedNewEntry");
        requestCounter = Monitors.newCounter(name + "_RequestEntry");
        Monitors.registerObject(this);
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
        return super.createEntry(rospl, op);
    }
    
    public final long getFreeEntryCount() {
        return freeEntryCounter.getValue();
    }
    
    public final long getCreatedEntryCount() {
        return createEntryCounter.getValue();
    }
    
    public final long getRequestsCount() {
        return requestCounter.getValue();
    }
}
