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
package com.netflix.loadbalancer;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.stats.distribution.DataDistribution;
import com.netflix.stats.distribution.DataPublisher;
import com.netflix.stats.distribution.Distribution;
import com.netflix.util.MeasuredRate;

/**
 * Capture various stats per Server(node) in the LoadBalancer
 * @author stonse
 *
 */
public class ServerStats {
    
    private static final int DEFAULT_PUBLISH_INTERVAL =  60 * 1000; // = 1 minute
    private static final int DEFAULT_BUFFER_SIZE = 60 * 1000; // = 1000 requests/sec for 1 minute
    private final DynamicIntProperty connectionFailureThreshold;    
    private final DynamicIntProperty circuitTrippedTimeoutFactor; 
    private final DynamicIntProperty maxCircuitTrippedTimeout;
    private static final DynamicIntProperty activeRequestsCountTimeout = 
        DynamicPropertyFactory.getInstance().getIntProperty("niws.loadbalancer.serverStats.activeRequestsCount.effectiveWindowSeconds", 60 * 10);
    
    private static final double[] PERCENTS = makePercentValues();
    
    private DataDistribution dataDist = new DataDistribution(1, PERCENTS); // in case
    private DataPublisher publisher = null;
    private final Distribution responseTimeDist = new Distribution();
    
    int bufferSize = DEFAULT_BUFFER_SIZE;
    int publishInterval = DEFAULT_PUBLISH_INTERVAL;
    
    
    long failureCountSlidingWindowInterval = 1000; 
    
    private MeasuredRate serverFailureCounts = new MeasuredRate(failureCountSlidingWindowInterval);
    private MeasuredRate requestCountInWindow = new MeasuredRate(300000L);
    
    Server server;
    
    AtomicLong totalRequests = new AtomicLong();
    
    @VisibleForTesting
    AtomicInteger successiveConnectionFailureCount = new AtomicInteger(0);
    
    @VisibleForTesting
    AtomicInteger activeRequestsCount = new AtomicInteger(0);
    
    private volatile long lastConnectionFailedTimestamp;
    private volatile long lastActiveRequestsCountChangeTimestamp;
    private AtomicLong totalCircuitBreakerBlackOutPeriod = new AtomicLong(0);
    private volatile long lastAccessedTimestamp;
    private volatile long firstConnectionTimestamp = 0;
    
    public ServerStats() {
        connectionFailureThreshold = DynamicPropertyFactory.getInstance().getIntProperty(
                "niws.loadbalancer.default.connectionFailureCountThreshold", 3);        
        circuitTrippedTimeoutFactor = DynamicPropertyFactory.getInstance().getIntProperty(
                "niws.loadbalancer.default.circuitTripTimeoutFactorSeconds", 10);

        maxCircuitTrippedTimeout = DynamicPropertyFactory.getInstance().getIntProperty(
                "niws.loadbalancer.default.circuitTripMaxTimeoutSeconds", 30);
    }
    
    public ServerStats(LoadBalancerStats lbStats) {
        this.maxCircuitTrippedTimeout = lbStats.getCircuitTripMaxTimeoutSeconds();
        this.circuitTrippedTimeoutFactor = lbStats.getCircuitTrippedTimeoutFactor();
        this.connectionFailureThreshold = lbStats.getConnectionFailureCountThreshold();
    }
    
    /**
     * Initializes the object, starting data collection and reporting.
     */
    public void initialize(Server server) {
        serverFailureCounts = new MeasuredRate(failureCountSlidingWindowInterval);
        requestCountInWindow = new MeasuredRate(300000L);
        if (publisher == null) {
            dataDist = new DataDistribution(getBufferSize(), PERCENTS);
            publisher = new DataPublisher(dataDist, getPublishIntervalMillis());
            publisher.start();
        }
        this.server = server;
    }
    
    private int getBufferSize() {
        return bufferSize;
    }

    private long getPublishIntervalMillis() {
        return publishInterval;
    }
    
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public void setPublishInterval(int publishInterval) {
        this.publishInterval = publishInterval;
    }

    /**
     * The supported percentile values.
     * These correspond to the various Monitor methods defined below.
     * No, this is not pretty, but that's the way it is.
     */
    private static enum Percent {

        TEN(10), TWENTY_FIVE(25), FIFTY(50), SEVENTY_FIVE(75), NINETY(90),
        NINETY_FIVE(95), NINETY_EIGHT(98), NINETY_NINE(99), NINETY_NINE_POINT_FIVE(99.5);

        private double val;

        Percent(double val) {
            this.val = val;
        }

        public double getValue() {
            return val;
        }

    }

    private static double[] makePercentValues() {
        Percent[] percents = Percent.values();
        double[] p = new double[percents.length];
        for (int i = 0; i < percents.length; i++) {
            p[i] = percents[i].getValue();
        }
        return p;
    }

    public long getFailureCountSlidingWindowInterval() {
        return failureCountSlidingWindowInterval;
    }

    public void setFailureCountSlidingWindowInterval(
            long failureCountSlidingWindowInterval) {
        this.failureCountSlidingWindowInterval = failureCountSlidingWindowInterval;
    }

    // run time methods
    
    
    /**
     * Increment the count of failures for this Server
     * 
     */
    public void addToFailureCount(){
        serverFailureCounts.increment();
    }
    
    /**
     * Returns the count of failures in the current window
     * 
     * @return
     */
    public long getFailureCount(){
        long count = 0;
        count = serverFailureCounts.getCurrentCount();
        return count;
    }
    
    /**
     * Call this method to note the response time after every request
     * @param msecs
     */
    public void noteResponseTime(double msecs){
        dataDist.noteValue(msecs);
        responseTimeDist.noteValue(msecs);
    }
    
    public void incrementNumRequests(){
        totalRequests.incrementAndGet();
    }
    
    public void incrementActiveRequestsCount() {        
        activeRequestsCount.incrementAndGet();
        requestCountInWindow.increment();
        long currentTime = System.currentTimeMillis();
        lastActiveRequestsCountChangeTimestamp = currentTime;
        lastAccessedTimestamp = currentTime;
        if (firstConnectionTimestamp == 0) {
            firstConnectionTimestamp = currentTime;
        }
    }

    public void decrementActiveRequestsCount() {
        if (activeRequestsCount.decrementAndGet() < 0) {
            activeRequestsCount.set(0);
        }
        lastActiveRequestsCountChangeTimestamp = System.currentTimeMillis();
    }
    
    public int getActiveRequestsCount() {
        return getActiveRequestsCount(System.currentTimeMillis());
    }

    public int getActiveRequestsCount(long currentTime) {
        int count = activeRequestsCount.get();
        if (count == 0) {
            return 0;
        } else if (currentTime - lastActiveRequestsCountChangeTimestamp > activeRequestsCountTimeout.get() * 1000 || count < 0) {
            activeRequestsCount.set(0);
            return 0;            
        } else {
            return count;
        }
    }

    
    public long getMeasuredRequestsCount() {
        return requestCountInWindow.getCount();
    }

    @Monitor(name="ActiveRequestsCount", type = DataSourceType.GAUGE)    
    public int getMonitoredActiveRequestsCount() {
        return activeRequestsCount.get();
    }
    
    @Monitor(name="CircuitBreakerTripped", type = DataSourceType.GAUGE)    
    public boolean isCircuitBreakerTripped() {
        return isCircuitBreakerTripped(System.currentTimeMillis());
    }
    
    public boolean isCircuitBreakerTripped(long currentTime) {
        long circuitBreakerTimeout = getCircuitBreakerTimeout();
        if (circuitBreakerTimeout <= 0) {
            return false;
        }
        return circuitBreakerTimeout > currentTime;
    }

    
    private long getCircuitBreakerTimeout() {
        long blackOutPeriod = getCircuitBreakerBlackoutPeriod();
        if (blackOutPeriod <= 0) {
            return 0;
        }
        return lastConnectionFailedTimestamp + blackOutPeriod;
    }
    
    private long getCircuitBreakerBlackoutPeriod() {
        int failureCount = successiveConnectionFailureCount.get();
        int threshold = connectionFailureThreshold.get();
        if (failureCount < threshold) {
            return 0;
        }
        int diff = (failureCount - threshold) > 16 ? 16 : (failureCount - threshold);
        int blackOutSeconds = (1 << diff) * circuitTrippedTimeoutFactor.get();
        if (blackOutSeconds > maxCircuitTrippedTimeout.get()) {
            blackOutSeconds = maxCircuitTrippedTimeout.get();
        }
        return blackOutSeconds * 1000L;
    }
    
    public void incrementSuccessiveConnectionFailureCount() {
        lastConnectionFailedTimestamp = System.currentTimeMillis();
        successiveConnectionFailureCount.incrementAndGet();
        totalCircuitBreakerBlackOutPeriod.addAndGet(getCircuitBreakerBlackoutPeriod());
    }
    
    public void clearSuccessiveConnectionFailureCount() {
        successiveConnectionFailureCount.set(0);
    }
    
    
    @Monitor(name="SuccessiveConnectionFailureCount", type = DataSourceType.GAUGE)
    public int getSuccessiveConnectionFailureCount() {
        return successiveConnectionFailureCount.get();
    }
    
    /*
     * Response total times
     */

    /**
     * Gets the average total amount of time to handle a request, in milliseconds.
     */
    @Monitor(name = "OverallResponseTimeMillisAvg", type = DataSourceType.INFORMATIONAL,
             description = "Average total time for a request, in milliseconds")
    public double getResponseTimeAvg() {
        return responseTimeDist.getMean();
    }

    /**
     * Gets the maximum amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "OverallResponseTimeMillisMax", type = DataSourceType.INFORMATIONAL,
             description = "Max total time for a request, in milliseconds")
    public double getResponseTimeMax() {
        return responseTimeDist.getMaximum();
    }

    /**
     * Gets the minimum amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "OverallResponseTimeMillisMin", type = DataSourceType.INFORMATIONAL,
             description = "Min total time for a request, in milliseconds")
    public double getResponseTimeMin() {
        return responseTimeDist.getMinimum();
    }

    /**
     * Gets the standard deviation in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "OverallResponseTimeMillisStdDev", type = DataSourceType.INFORMATIONAL,
             description = "Standard Deviation in total time to handle a request, in milliseconds")
    public double getResponseTimeStdDev() {
        return responseTimeDist.getStdDev();
    }

    /*
     * QOS percentile performance data for most recent period
     */

    /**
     * Gets the number of samples used to compute the various response-time percentiles.
     */
    @Monitor(name = "ResponseTimePercentileNumValues", type = DataSourceType.GAUGE,
             description = "The number of data points used to compute the currently reported percentile values")
    public int getResponseTimePercentileNumValues() {
        return dataDist.getSampleSize();
    }

    /**
     * Gets the time when the varios percentile data was last updated.
     */
    @Monitor(name = "ResponseTimePercentileWhen", type = DataSourceType.INFORMATIONAL,
             description = "The time the percentile values were computed")
    public String getResponseTimePercentileTime() {
        return dataDist.getTimestamp();
    }

    /**
     * Gets the time when the varios percentile data was last updated,
     * in milliseconds since the epoch.
     */
    @Monitor(name = "ResponseTimePercentileWhenMillis", type = DataSourceType.COUNTER,
             description = "The time the percentile values were computed in milliseconds since the epoch")
    public long getResponseTimePercentileTimeMillis() {
        return dataDist.getTimestampMillis();
    }

    /**
     * Gets the average total amount of time to handle a request
     * in the recent time-slice, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillisAvg", type = DataSourceType.GAUGE,
             description = "Average total time for a request in the recent time slice, in milliseconds")
    public double getResponseTimeAvgRecent() {
        return dataDist.getMean();
    }
    
    /**
     * Gets the 10-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis10Percentile", type = DataSourceType.INFORMATIONAL,
             description = "10th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime10thPercentile() {
        return getResponseTimePercentile(Percent.TEN);
    }

    /**
     * Gets the 25-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis25Percentile", type = DataSourceType.INFORMATIONAL,
             description = "25th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime25thPercentile() {
        return getResponseTimePercentile(Percent.TWENTY_FIVE);
    }

    /**
     * Gets the 50-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis50Percentile", type = DataSourceType.INFORMATIONAL,
             description = "50th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime50thPercentile() {
        return getResponseTimePercentile(Percent.FIFTY);
    }

    /**
     * Gets the 75-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis75Percentile", type = DataSourceType.INFORMATIONAL,
             description = "75th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime75thPercentile() {
        return getResponseTimePercentile(Percent.SEVENTY_FIVE);
    }

    /**
     * Gets the 90-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis90Percentile", type = DataSourceType.INFORMATIONAL,
             description = "90th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime90thPercentile() {
        return getResponseTimePercentile(Percent.NINETY);
    }

    /**
     * Gets the 95-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis95Percentile", type = DataSourceType.GAUGE,
             description = "95th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime95thPercentile() {
        return getResponseTimePercentile(Percent.NINETY_FIVE);
    }

    /**
     * Gets the 98-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis98Percentile", type = DataSourceType.INFORMATIONAL,
             description = "98th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime98thPercentile() {
        return getResponseTimePercentile(Percent.NINETY_EIGHT);
    }

    /**
     * Gets the 99-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis99Percentile", type = DataSourceType.GAUGE,
             description = "99th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime99thPercentile() {
        return getResponseTimePercentile(Percent.NINETY_NINE);
    }

    /**
     * Gets the 99.5-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis99_5Percentile", type = DataSourceType.GAUGE,
             description = "99.5th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime99point5thPercentile() {
        return getResponseTimePercentile(Percent.NINETY_NINE_POINT_FIVE);
    }

    public long getTotalRequestsCount() {
        return totalRequests.get();
    }
    
    private double getResponseTimePercentile(Percent p) {
        return dataDist.getPercentiles()[p.ordinal()];
    }
    
    public String toString(){
        StringBuilder sb = new StringBuilder();
        
        sb.append("[Server:" + server + ";");
        sb.append("\tZone:" + server.getZone() + ";");
        sb.append("\tTotal Requests:" + totalRequests + ";");
        sb.append("\tSuccessive connection failure:" + getSuccessiveConnectionFailureCount() + ";");
        if (isCircuitBreakerTripped()) {
            sb.append("\tBlackout until: " + new Date(getCircuitBreakerTimeout()) + ";");
        }
        sb.append("\tTotal blackout seconds:" + totalCircuitBreakerBlackOutPeriod.get() / 1000 + ";");
        sb.append("\tLast connection made:" + new Date(lastAccessedTimestamp) + ";");
        if (lastConnectionFailedTimestamp > 0) {
            sb.append("\tLast connection failure: " + new Date(lastConnectionFailedTimestamp)  + ";");
        }
        sb.append("\tFirst connection made: " + new Date(firstConnectionTimestamp)  + ";");
        sb.append("\tActive Connections:" + getMonitoredActiveRequestsCount()  + ";");
        sb.append("\ttotal failure count in last (" + failureCountSlidingWindowInterval + ") msecs:" + getFailureCount()  + ";");
        sb.append("\taverage resp time:" + getResponseTimeAvg()  + ";");
        sb.append("\t90 percentile resp time:" + getResponseTime90thPercentile()  + ";");
        sb.append("\t95 percentile resp time:" + getResponseTime95thPercentile()  + ";");
        sb.append("\tmin resp time:" + getResponseTimeMin()  + ";");
        sb.append("\tmax resp time:" + getResponseTimeMax()  + ";");
        sb.append("\tstddev resp time:" + getResponseTimeStdDev());
        sb.append("]\n");
        
        return sb.toString();
    }
    
    public static void main(String[] args){
        ServerStats ss = new ServerStats();
        ss.setBufferSize(1000);
        ss.setPublishInterval(1000);
        ss.initialize(new Server("stonse", 80));
        
        Random r = new Random(1459834);
        for (int i=0; i < 99; i++){
            double rl = r.nextDouble() * 25.2;
            ss.noteResponseTime(rl);
            ss.incrementNumRequests();
            try {
                Thread.sleep(100);
                System.out.println("ServerStats:avg:" + ss.getResponseTimeAvg());
                System.out.println("ServerStats:90 percentile:" + ss.getResponseTime90thPercentile());
                System.out.println("ServerStats:90 percentile:" + ss.getResponseTimePercentileNumValues());
                
            } catch (InterruptedException e) {
                
            }
           
        }
        System.out.println("done ---");
        ss.publisher.stop();
        
        System.out.println("ServerStats:" + ss);
     
        
    }
    
}
