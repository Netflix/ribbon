package com.netflix.loadbalancer;

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
