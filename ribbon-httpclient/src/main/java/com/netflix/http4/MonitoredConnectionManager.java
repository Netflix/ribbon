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

import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.conn.tsccm.AbstractConnPool;
import org.apache.http.impl.conn.tsccm.ConnPoolByRoute;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;

import com.google.common.annotations.VisibleForTesting;

/**
 * A connection manager that uses {@link NamedConnectionPool}, which provides
 * connection reuse statistics, as its underlying connection pool.
 * 
 * @author awang
 *
 */
public class MonitoredConnectionManager extends ThreadSafeClientConnManager {
    
    public MonitoredConnectionManager(String name) {
        super();
        initMonitors(name);
    }

    public MonitoredConnectionManager(String name, SchemeRegistry schreg, long connTTL,
            TimeUnit connTTLTimeUnit) {
        super(schreg, connTTL, connTTLTimeUnit);
        initMonitors(name);
    }

    public MonitoredConnectionManager(String name, SchemeRegistry schreg) {
        super(schreg);
        initMonitors(name);
    }
    
    void initMonitors(String name) {
        if (this.pool instanceof NamedConnectionPool) {
            ((NamedConnectionPool) this.pool).initMonitors(name);
        }
    }

    @Override
    @Deprecated
    protected AbstractConnPool createConnectionPool(HttpParams params) {
         return new NamedConnectionPool(connOperator, params);
    }

    @Override
    protected ConnPoolByRoute createConnectionPool(long connTTL,
            TimeUnit connTTLTimeUnit) {
        return new NamedConnectionPool(connOperator, connPerRoute, 20, connTTL, connTTLTimeUnit);
    }

    @VisibleForTesting
    ConnPoolByRoute getConnectionPool() {
        return this.pool;
    }

    @Override
    public ClientConnectionRequest requestConnection(HttpRoute route,
            Object state) {
        // TODO Auto-generated method stub
        return super.requestConnection(route, state);
    }
}
