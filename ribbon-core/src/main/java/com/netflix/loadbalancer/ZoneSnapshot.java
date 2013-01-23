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

/**
 * Captures the metrics on a Per Zone basis (Zone is modeled after the Amazon Availability Zone)
 * @author awang
 *
 */
public class ZoneSnapshot {
    final int instanceCount;
    final double loadPerServer;
    final int circuitTrippedCount;
    final int activeRequestsCount;
    
    public ZoneSnapshot() {
        this(0, 0, 0, 0d);
    }
    
    public ZoneSnapshot(int instanceCount, int circuitTrippedCount, int activeRequestsCount, double loadPerServer) {
        this.instanceCount = instanceCount;
        this.loadPerServer = loadPerServer;
        this.circuitTrippedCount = circuitTrippedCount;
        this.activeRequestsCount = activeRequestsCount;
    }
    
    public final int getInstanceCount() {
        return instanceCount;
    }
    
    public final double getLoadPerServer() {
        return loadPerServer;
    }
    
    public final int getCircuitTrippedCount() {
        return circuitTrippedCount;
    }
    
    public final int getActiveRequestsCount() {
        return activeRequestsCount;
    }

    @Override
    public String toString() {
        return "ZoneSnapshot [instanceCount=" + instanceCount
                + ", loadPerServer=" + loadPerServer + ", circuitTrippedCount="
                + circuitTrippedCount + ", activeRequestsCount="
                + activeRequestsCount + "]";
    }
}
