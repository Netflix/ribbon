package com.netflix.niws.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicDoubleProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.NFLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ZoneSnapshot;

public class ZoneAvoidanceRule extends AvailabilityFilteringRule {
    
    private  static final DynamicDoubleProperty triggeringLoad = 
        DynamicPropertyFactory.getInstance().getDoubleProperty("niws.loadbalancer.zoneAvoidanceRule.triggeringLoadThreshold", 0.2d);

    private static final DynamicBooleanProperty ENABLED = DynamicPropertyFactory.getInstance().getBooleanProperty("niws.loadbalancer.zoneAvoidanceRule.enabled", true);
    
    private static final Random random = new Random();

    @Override
    public Server choose(NFLoadBalancer lb, Object key) {
        LoadBalancerStats lbStats = lb.getLoadBalancerStats();
        Server server = super.choose(lb, key);
        if (lbStats == null || !ENABLED.get() || server.getZone() == null || lbStats.getAvailableZones().size() <= 1) {
            return server;
        } else {
            Set<String> availableZones = getAvailableZones(lbStats, triggeringLoad.get(), 0.99999d);
            if (availableZones == null || availableZones.size() == 0) {
                return server;
            } else {
                int count = lb.getServerList(false).size();
                while (!availableZones.contains(server.getZone()) && (--count >= 0)) {
                    server = super.choose(lb, key);
                }
                return server;
            }
        }
    }
    
    static Map<String, ZoneSnapshot> createSnapshot(LoadBalancerStats lbStats) {
        Map<String, ZoneSnapshot> map = new HashMap<String, ZoneSnapshot>();
        for (String zone: lbStats.getAvailableZones()) {    
            ZoneSnapshot snapshot = lbStats.getZoneSnapshot(zone);
            map.put(zone, snapshot);
        }
        return map;
    }

    static String randomChooseZone(Map<String, ZoneSnapshot> snapshot, Set<String> chooseFrom) {
        if (chooseFrom == null || chooseFrom.size() == 0) {
            return null;
        }
        String selectedZone = chooseFrom.iterator().next();
        if (chooseFrom.size() == 1) {
            return selectedZone;
        }
        int totalServerCount = 0;
        for (String zone: chooseFrom) {
            totalServerCount += snapshot.get(zone).getInstanceCount();
        }
        int index = random.nextInt(totalServerCount) + 1;
        int sum = 0;
        for (String zone: chooseFrom) {
            sum += snapshot.get(zone).getInstanceCount();
            if (index <= sum) {
                selectedZone = zone;
                break;
            }
        }
        return selectedZone;                    
    }
    
    public static Set<String> getAvailableZones(Map<String, ZoneSnapshot> snapshot, double triggeringLoad, double triggeringBlackoutPercentage) {
        if (snapshot.isEmpty()) {
            return null;
        }
        Set<String> availableZones = new HashSet<String>(snapshot.keySet());
        if (availableZones.size() == 1) {
            return availableZones;
        }
        Set<String> worstZones = new HashSet<String>();
        double maxLoadPerServer = 0;
        boolean limitedZoneAvailability = false;

        for (Map.Entry<String, ZoneSnapshot> zoneEntry: snapshot.entrySet()) {
            String zone = zoneEntry.getKey();            
            ZoneSnapshot zoneSnapshot = zoneEntry.getValue();
            int instanceCount = zoneSnapshot.getInstanceCount();
            if (instanceCount == 0) {
                availableZones.remove(zone);
                limitedZoneAvailability = true;
            } else {
                double loadPerServer = zoneSnapshot.getLoadPerServer();
                if (((double) zoneSnapshot.getCircuitTrippedCount()) / instanceCount >= triggeringBlackoutPercentage
                        || loadPerServer < 0) {
                    availableZones.remove(zone);
                    limitedZoneAvailability = true;
                } else {                
                    if (Math.abs(loadPerServer - maxLoadPerServer) < 0.000001d) {
                        // they are the same considering double calculation round error
                        worstZones.add(zone);
                    } else if (loadPerServer > maxLoadPerServer) {
                        maxLoadPerServer = loadPerServer;
                        worstZones.clear();
                        worstZones.add(zone);
                    } 
                }       
            }
        }
                 
        if (maxLoadPerServer < triggeringLoad && !limitedZoneAvailability) {
            // zone override is not needed here
            return availableZones;
        }
        String zoneToAvoid = randomChooseZone(snapshot, worstZones);
        if (zoneToAvoid != null) {
            availableZones.remove(zoneToAvoid);
        } 
        return availableZones;
        
    }
    
    
    public static Set<String> getAvailableZones(LoadBalancerStats lbStats, double triggeringLoad, double triggeringBlackoutPercentage) {
        if (lbStats == null) {
            return null;
        }
        Map<String, ZoneSnapshot> snapshot = createSnapshot(lbStats);
        return getAvailableZones(snapshot, triggeringLoad, triggeringBlackoutPercentage);
    }
}
