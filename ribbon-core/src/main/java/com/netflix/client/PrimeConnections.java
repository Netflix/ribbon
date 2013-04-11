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
package com.netflix.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.Server;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;

/**
 * Prime the connections for a given Client (For those Client that
 * have a LoadBalancer that knows the set of Servers it will connect to) This is
 * mainly done to address those deployment environments (Read EC2) which benefit
 * from a firewall connection/path warmup prior to actual use for live requests.
 * <p>
 * This class is not protocol specific. Actual priming operation is delegated to 
 * instance of {@link IPrimeConnection}, which is instantiated using reflection
 * according to property {@link CommonClientConfigKey#PrimeConnectionsClassName}.
 * 
 * @author stonse
 * @author awang
 * 
 */
public class PrimeConnections {

    public static interface PrimeConnectionListener {
        public void primeCompleted(Server s, Throwable lastException);
    }
    
    static class PrimeConnectionCounters {
        final AtomicInteger numServersLeft;
        final AtomicInteger numServers;
        final AtomicInteger numServersSuccessful;    
        public PrimeConnectionCounters(int initialSize) {
            numServersLeft = new AtomicInteger(initialSize);
            numServers = new AtomicInteger(initialSize);
            numServersSuccessful = new AtomicInteger(0);
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(PrimeConnections.class);

    // affordance to change the URI we connect to while "priming"
    // default of "/" is good for most - but if its heavy operation on
    // the server side, then a more lightweight URI can be chosen
    String primeConnectionsURIPath = "/";

    /**
     * Executor service for executing asynchronous requests.
     */

    private ExecutorService executorService;

    private int maxExecutorThreads = 5;

    private long executorThreadTimeout = 30000;

    private String name = "default";

    private int maxTasksPerExecutorQueue = 100;
    
    private float primeRatio = 1.0f;


    int maxRetries = 9;

    long maxTotalTimeToPrimeConnections = 30 * 1000; // default time

    long totalTimeTaken = 0; // Total time taken

    private boolean aSync = true;
        
    Counter totalCounter;
    Counter successCounter;
    Timer initialPrimeTimer;
    
    private IPrimeConnection connector;

    private PrimeConnections() {
    }

    public PrimeConnections(String name, IClientConfig niwsClientConfig) {
        int maxRetriesPerServerPrimeConnection = Integer.valueOf(DefaultClientConfigImpl.DEFAULT_MAX_RETRIES_PER_SERVER_PRIME_CONNECTION);
        long maxTotalTimeToPrimeConnections = Long.valueOf(DefaultClientConfigImpl.DEFAULT_MAX_TOTAL_TIME_TO_PRIME_CONNECTIONS);
        String primeConnectionsURI = DefaultClientConfigImpl.DEFAULT_PRIME_CONNECTIONS_URI;  
        String className = DefaultClientConfigImpl.DEFAULT_PRIME_CONNECTIONS_CLASS;
        try {
            maxRetriesPerServerPrimeConnection = Integer.parseInt(String.valueOf(niwsClientConfig.getProperty(
                    CommonClientConfigKey.MaxRetriesPerServerPrimeConnection, maxRetriesPerServerPrimeConnection)));
        } catch (Exception e) {
            logger.warn("Invalid maxRetriesPerServerPrimeConnection");
        }
        try {
            maxTotalTimeToPrimeConnections = Long.parseLong(String.valueOf(niwsClientConfig.getProperty(
                    CommonClientConfigKey.MaxTotalTimeToPrimeConnections,maxTotalTimeToPrimeConnections)));
        } catch (Exception e) {
            logger.warn("Invalid maxTotalTimeToPrimeConnections");
        }
        primeConnectionsURI = String.valueOf(niwsClientConfig.getProperty(CommonClientConfigKey.PrimeConnectionsURI, primeConnectionsURI));
        float primeRatio = Float.parseFloat(String.valueOf(niwsClientConfig.getProperty(CommonClientConfigKey.MinPrimeConnectionsRatio)));
        className = (String) niwsClientConfig.getProperty(CommonClientConfigKey.PrimeConnectionsClassName, 
        		DefaultClientConfigImpl.DEFAULT_PRIME_CONNECTIONS_CLASS);
        try {
            connector = (IPrimeConnection) Class.forName(className).newInstance();
            connector.initWithNiwsConfig(niwsClientConfig);
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize prime connections", e);
        }
        setUp(name, maxRetriesPerServerPrimeConnection, 
                maxTotalTimeToPrimeConnections, primeConnectionsURI, primeRatio);        
    }
        
    public PrimeConnections(String name, int maxRetries, 
            long maxTotalTimeToPrimeConnections, String primeConnectionsURI) {
        setUp(name, maxRetries, maxTotalTimeToPrimeConnections, primeConnectionsURI, DefaultClientConfigImpl.DEFAULT_MIN_PRIME_CONNECTIONS_RATIO);
    }

    public PrimeConnections(String name, int maxRetries, 
            long maxTotalTimeToPrimeConnections, String primeConnectionsURI, float primeRatio) {
        setUp(name, maxRetries, maxTotalTimeToPrimeConnections, primeConnectionsURI, primeRatio);
    }

    private void setUp(String name, int maxRetries, 
            long maxTotalTimeToPrimeConnections, String primeConnectionsURI, float primeRatio) {        
        this.name = name;
        this.maxRetries = maxRetries;
        this.maxTotalTimeToPrimeConnections = maxTotalTimeToPrimeConnections;
        this.primeConnectionsURIPath = primeConnectionsURI;        
        this.primeRatio = primeRatio;
        executorService = new ThreadPoolExecutor(1 /* minimum */,
                maxExecutorThreads /* max threads */,
                executorThreadTimeout /*
                                       * timeout - same property as create
                                       * timeout
                                       */, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(maxTasksPerExecutorQueue)
                /* Bounded queue with FIFO- bounded to max tasks */,
                new ASyncPrimeConnectionsThreadFactory(name) /*
                                                              * So we can give
                                                              * our Thread a
                                                              * name
                                                              */
        );        
        totalCounter = Monitors.newCounter(name + "_PrimeConnection_TotalCounter");
        successCounter = Monitors.newCounter(name + "_PrimeConnection_SuccessCounter");
        initialPrimeTimer = Monitors.newTimer(name + "_initialPrimeConnectionsTimer", TimeUnit.MILLISECONDS);
        Monitors.registerObject(name + "_PrimeConnection", this);
    }
    
    /**
     * Prime connections, blocking until configured percentage (default is 100%) of target servers are primed 
     * or max time is reached.
     * 
     * @see CommonClientConfigKey#MinPrimeConnectionsRatio
     * @see CommonClientConfigKey#MaxTotalTimeToPrimeConnections
     * 
     */
    public void primeConnections(List<Server> servers) {
        if (servers == null || servers.size() == 0) {
            logger.debug("No server to prime");
            return;
        }
        for (Server server: servers) {
            server.setReadyToServe(false);
        }
        int totalCount = (int) (servers.size() * primeRatio); 
        final CountDownLatch latch = new CountDownLatch(totalCount);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount= new AtomicInteger(0);
        primeConnectionsAsync(servers, new PrimeConnectionListener()  {            
            @Override
            public void primeCompleted(Server s, Throwable lastException) {
                if (lastException == null) {
                    successCount.incrementAndGet();
                    s.setReadyToServe(true);
                } else {
                    failureCount.incrementAndGet();
                }
                latch.countDown();
            }
        }); 
                
        Stopwatch stopWatch = initialPrimeTimer.start();
        try {
            latch.await(maxTotalTimeToPrimeConnections, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Priming connection interrupted", e);
        } finally {
            stopWatch.stop();
        }
        printStats(totalCount, successCount.get(), failureCount.get(), stopWatch.getDuration(TimeUnit.MILLISECONDS));
    }
    
    private void printStats(int total, int success, int failure, long totalTime) {
        if (total != success) {
            logger.info("Priming Connections not fully successful");
        } else {
            logger.info("Priming connections fully successful");
        }
        logger.debug("numServers left to be 'primed'="
                + (total - success));
        logger.debug("numServers successfully 'primed'=" + success);
        logger
                .debug("numServers whose attempts not complete exclusively due to max time allocated="
                        + (total - (success + failure)));
        logger.debug("Total Time Taken=" + totalTime
                + " msecs, out of an allocated max of (msecs)="
                + maxTotalTimeToPrimeConnections);
    }

    /*
    private void makeConnectionsASync() {
        Callable<Void> ft = new Callable<Void>() {
            public Void call() throws Exception {
                logger.info("primeConnections ...");
                makeConnections();
                return null;
            }
        };
        outerExecutorService.submit(ft);
    }
    */
    
    /**
     * Prime servers asynchronously.
     * 
     * @param servers
     * @param listener
     * @return
     */
    public List<Future<Boolean>> primeConnectionsAsync(final List<Server> servers, final PrimeConnectionListener listener) {
        if (servers == null) {
            return Collections.emptyList();
        }
        List<Server> allServers = new ArrayList<Server>();
        allServers.addAll(servers);
        if (allServers.size() == 0){
            logger.debug("RestClient:" + name + ". No nodes/servers to prime connections");
            return Collections.emptyList();
        }        

        logger.info("Priming Connections for RestClient:" + name
                + ", numServers:" + allServers.size());
        List<Future<Boolean>> ftList = new ArrayList<Future<Boolean>>();
        for (Server s : allServers) {
            // prevent the server to be used by load balancer
            // will be set to true when priming is done
            s.setReadyToServe(false);
            if (aSync) {
                Future<Boolean> ftC = null;
                try {
                    ftC = makeConnectionASync(s, listener);
                    ftList.add(ftC);

                } catch (Throwable e) { // NOPMD
                    // It does not really matter if there was an exception,
                    // the goal here is to attempt "priming/opening" the route
                    // in ec2 .. actual http results do not matter
                }
            } else {
                connectToServer(s, listener);
            }
        }   
        return ftList;
    }
    
    private Future<Boolean> makeConnectionASync(final Server s, 
            final PrimeConnectionListener listener) throws InterruptedException, ExecutionException {
        Callable<Boolean> ftConn = new Callable<Boolean>() {
            public Boolean call() throws Exception {
                logger.debug("calling primeconnections ...");
                return connectToServer(s, listener);
            }
        };
        return executorService.submit(ftConn);
    }

    void shutdown() {
        executorService.shutdown();
    }

    private Boolean connectToServer(final Server s, final PrimeConnectionListener listener) {
        int tryNum = 0;
        Exception lastException = null;
        totalCounter.increment();
        boolean success = false;
        do {
            try {
                logger.debug("Executing PrimeConnections request to server " + s + " with path " + primeConnectionsURIPath
                        + ", tryNum=" + tryNum);
                success = connector.connect(s, primeConnectionsURIPath);
                successCounter.increment();
                break;
            } catch (Exception e) {
                // It does not really matter if there was an exception,
                // the goal here is to attempt "priming/opening" the route
                // in ec2 .. actual http results do not matter
                logger.debug("Error connecting to server: {}", e.getMessage());
                lastException = e;
                sleepBeforeRetry(tryNum);
            } 
            logger.debug("server:" + s + ", result=" + success + ", tryNum="
                    + tryNum + ", maxRetries=" + maxRetries);
            tryNum++;
        } while (!success && (tryNum <= maxRetries));
        // set the alive flag so that it can be used by load balancers
        if (listener != null) {
            try {
                listener.primeCompleted(s, lastException);
            } catch (Throwable e) {
                logger.error("Error calling PrimeComplete listener", e);
            }
        }
        logger.debug("Either done, or quitting server:" + s + ", result="
                + success + ", tryNum=" + tryNum + ", maxRetries=" + maxRetries);        
        return success;
    }

    private void sleepBeforeRetry(int tryNum) {
        try {
            int sleep = (tryNum + 1) * 100;
            logger.debug("Sleeping for " + sleep + "ms ...");
            Thread.sleep(sleep); // making this seconds based is too slow
            // i.e. 200ms, 400 ms, 800ms, 1600ms etc.
        } catch (InterruptedException ex) {
        }
    }
    
    static class ASyncPrimeConnectionsThreadFactory implements ThreadFactory {
        private static final AtomicInteger groupNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        ASyncPrimeConnectionsThreadFactory(String name) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup(); // NOPMD
            namePrefix = "ASyncPrimeConnectionsThreadFactory-" + name + "-"
                    + groupNumber.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix
                    + threadNumber.getAndIncrement(), 0);
            if (!t.isDaemon())
                t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
