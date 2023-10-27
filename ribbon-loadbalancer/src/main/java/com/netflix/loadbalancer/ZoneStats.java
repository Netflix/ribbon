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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.patterns.PolledMeter;

/**
 * Class that stores Statistics per Zone (where Zone is typically a Amazon
 * Availability Zone)
 * 
 * @author awang
 * 
 * @param <T>
 */
public class ZoneStats<T extends Server> {

    private final LoadBalancerStats loadBalancerStats;
    private final String zone;
    private static final String PREFIX = "ZoneStats_";
    private final Counter counter;
    
    final String monitorId;

    public ZoneStats(String name, String zone, LoadBalancerStats loadBalancerStats) {
        this(name, zone, loadBalancerStats, Spectator.globalRegistry());
    }

    public ZoneStats(String name, String zone, LoadBalancerStats loadBalancerStats, Registry registry) {
        this.zone = zone;
        this.loadBalancerStats = loadBalancerStats;
        monitorId = name + ":" + zone;  
        counter = registry.counter(PREFIX + name + "_" + zone + "_Counter");

        PolledMeter.using(registry).withName(PREFIX + "InstanceCount").monitorValue(this, ZoneStats::getInstanceCount);
        PolledMeter.using(registry).withName(PREFIX + "CircuitBreakerTrippedCount").monitorValue(this, ZoneStats::getCircuitBreakerTrippedCount);
        PolledMeter.using(registry).withName(PREFIX + "ActiveRequestsPerServer").monitorValue(this, ZoneStats::getActiveRequestsPerServer);
    }
    
    public final String getZone() {
        return zone;
    }

    public int getActiveRequestsCount() {
        return loadBalancerStats.getActiveRequestsCount(zone);
    }

    public int getInstanceCount() {
        return loadBalancerStats.getInstanceCount(zone);
    }
        
    public int getCircuitBreakerTrippedCount() {
        return loadBalancerStats.getCircuitBreakerTrippedCount(zone);
    }

    public double getActiveRequestsPerServer() {
        return loadBalancerStats.getActiveRequestsPerServer(zone);
    }

    public long getMeasuredZoneHits() {
        return loadBalancerStats.getMeasuredZoneHits(zone);
    }

    public double getCircuitBreakerTrippedPercentage() {
        ZoneSnapshot snapShot = loadBalancerStats.getZoneSnapshot(zone);
        int totalCount = snapShot.getInstanceCount();
        int circuitTrippedCount = snapShot.getCircuitTrippedCount();
        if (totalCount == 0) {
            if (circuitTrippedCount != 0) {
                return -1;
            } else {
                return 0;
            }
        } else {
            return snapShot.getCircuitTrippedCount() / ((double) totalCount); 
        }
    }
    
    void incrementCounter() {
        counter.increment();
    }
    
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();        
        sb.append("[Zone:" + zone + ";");
        sb.append("\tInstance count:" + getInstanceCount() + ";");
        sb.append("\tActive connections count: " + getActiveRequestsCount() + ";");
        sb.append("\tCircuit breaker tripped count: " + getCircuitBreakerTrippedCount() + ";");
        sb.append("\tActive connections per server: " + getActiveRequestsPerServer() + ";");        
        sb.append("]\n");        
        return sb.toString();
    }
}
