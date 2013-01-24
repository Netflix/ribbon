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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.http.conn.ClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;

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
    Timer timer;
    
   
    
    private DynamicIntProperty connIdleEvictTimeMilliSeconds 
        = DynamicPropertyFactory.getInstance().getIntProperty("default.nfhttpclient.connIdleEvictTimeMilliSeconds", 
                NFHttpClientConstants.DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS);
    
    boolean enableConnectionPoolCleanerTask = false;
    long connectionCleanerTimerDelay = 10;
    long connectionCleanerRepeatInterval = NFHttpClientConstants.DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS;
    
    public ConnectionPoolCleaner(String name, ClientConnectionManager connMgr){
        this.name = name;
        this.connMgr = connMgr;        
    }
    
    public DynamicIntProperty getConnIdleEvictTimeMilliSeconds() {
        return connIdleEvictTimeMilliSeconds;
    }

    public void setConnIdleEvictTimeMilliSeconds(
            DynamicIntProperty connIdleEvictTimeMilliSeconds) {
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
        if (enableConnectionPoolCleanerTask){
            timer = new Timer(name + "-ConnectionPoolCleanerThread", true);             
            timer.schedule(new TimerTask() {
                   
                    public void run() {
                        try {
                            cleanupConnections();
                        } catch (Throwable e) {
                            logger.error("Exception in ConnectionPoolCleanerThread",e);
                            //e.printStackTrace();
                        }
                    }
                }, connectionCleanerTimerDelay, connectionCleanerRepeatInterval);
            logger.info("Initializing ConnectionPoolCleaner for NFHttpClient:" + name);
            // Add it to the shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run()
                {
                    logger.info("Stopping the ConnectionPoolCleaner Update Task");                    
                    timer.cancel();
                }
            }));
        }
    }
    
    void cleanupConnections(){
        connMgr.closeExpiredConnections();
        connMgr.closeIdleConnections(connIdleEvictTimeMilliSeconds.get(), TimeUnit.MILLISECONDS);       
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
