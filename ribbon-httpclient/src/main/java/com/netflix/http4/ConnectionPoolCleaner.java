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

import com.netflix.client.config.Property;
import org.apache.http.conn.ClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Class that is responsible to cleanup connections based on a policy
 * For e.g. evict all connections from the pool that have been idle for more than x msecs
 * @author stonse
 *
 */
public class ConnectionPoolCleaner {   

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolCleaner.class);
    
    String name = "default";    
    ClientConnectionManager connMgr;
    ScheduledExecutorService scheduler;

    private Property<Integer> connIdleEvictTimeMilliSeconds = Property.of(30*1000);
    
    volatile boolean enableConnectionPoolCleanerTask = false;
    long connectionCleanerTimerDelay = 10;
    long connectionCleanerRepeatInterval = 30*1000;
    private volatile ScheduledFuture<?> scheduledFuture;
    
    public ConnectionPoolCleaner(String name, ClientConnectionManager connMgr, ScheduledExecutorService scheduler){
        this.name = name;
        this.connMgr = connMgr;     
        this.scheduler = scheduler;
    }
    
    public Property<Integer> getConnIdleEvictTimeMilliSeconds() {
        return connIdleEvictTimeMilliSeconds;
    }

    public void setConnIdleEvictTimeMilliSeconds(Property<Integer> connIdleEvictTimeMilliSeconds) {
        this.connIdleEvictTimeMilliSeconds = connIdleEvictTimeMilliSeconds;
    }

   
    public boolean isEnableConnectionPoolCleanerTask() {
        return enableConnectionPoolCleanerTask;
    }

    public void setEnableConnectionPoolCleanerTask(
            boolean enableConnectionPoolCleanerTask) {
        this.enableConnectionPoolCleanerTask = enableConnectionPoolCleanerTask;
    }

    public long getConnectionCleanerTimerDelay() {
        return connectionCleanerTimerDelay;
    }

    public void setConnectionCleanerTimerDelay(long connectionCleanerTimerDelay) {
        this.connectionCleanerTimerDelay = connectionCleanerTimerDelay;
    }

    public long getConnectionCleanerRepeatInterval() {
        return connectionCleanerRepeatInterval;
    }

    public void setConnectionCleanerRepeatInterval(
            long connectionCleanerRepeatInterval) {
        this.connectionCleanerRepeatInterval = connectionCleanerRepeatInterval;
    }

    public void initTask(){
        if (enableConnectionPoolCleanerTask) {
            scheduledFuture = scheduler.scheduleWithFixedDelay(new Runnable() {
                public void run() {
                    try {
                        if (enableConnectionPoolCleanerTask) {
                            logger.debug("Connection pool clean up started for client {}", name);
                            cleanupConnections();
                        } else if (scheduledFuture != null) {
                            scheduledFuture.cancel(true);
                        }
                    } catch (Throwable e) {
                        logger.error("Exception in ConnectionPoolCleanerThread",e);
                    }
                }
            }, connectionCleanerTimerDelay, connectionCleanerRepeatInterval, TimeUnit.MILLISECONDS);
            logger.info("Initializing ConnectionPoolCleaner for NFHttpClient:" + name);
        }
    }
    
    void cleanupConnections(){
        connMgr.closeExpiredConnections();
        connMgr.closeIdleConnections(connIdleEvictTimeMilliSeconds.getOrDefault(), TimeUnit.MILLISECONDS);
    }
    
    public void shutdown() {
        enableConnectionPoolCleanerTask = false;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }
    
    public String toString(){
        StringBuilder sb = new StringBuilder();
        
        sb.append("ConnectionPoolCleaner:" + name);
        sb.append(", connIdleEvictTimeMilliSeconds:" + connIdleEvictTimeMilliSeconds.get());
        sb.append(", connectionCleanerTimerDelay:" + connectionCleanerTimerDelay);
        sb.append(", connectionCleanerRepeatInterval:" + connectionCleanerRepeatInterval);
        
        return sb.toString();
    }
    
    
    
}
