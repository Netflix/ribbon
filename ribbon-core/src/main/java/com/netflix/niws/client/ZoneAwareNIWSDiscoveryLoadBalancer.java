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
package com.netflix.niws.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicDoubleProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ZoneSnapshot;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;

public class ZoneAwareNIWSDiscoveryLoadBalancer<T extends Server> extends DynamicServerListLoadBalancer<T> {

    private ConcurrentHashMap<String, BaseLoadBalancer> balancers = new ConcurrentHashMap<String, BaseLoadBalancer>();
    
    private static final Logger logger = LoggerFactory.getLogger(ZoneAwareNIWSDiscoveryLoadBalancer.class);
    
    private static final Random random = new Random();
        
    private volatile DynamicDoubleProperty triggeringLoad;

    private volatile DynamicDoubleProperty triggeringBlackoutPercentage; 

    private static final DynamicBooleanProperty ENABLED = DynamicPropertyFactory.getInstance().getBooleanProperty("ZoneAwareNIWSDiscoveryLoadBalancer.enabled", true);
    
    private Timer chooseServerTimer;
        
    String monitorId;
        
    void setUpServerList(List<Server> upServerList) {
        this.upServerList = upServerList;
    }
        
    @Override
    protected void setServerListForZones(Map<String, List<Server>> zoneServersMap) {
        super.setServerListForZones(zoneServersMap);
        for (Map.Entry<String, List<Server>> entry: zoneServersMap.entrySet()) {
        	String zone = entry.getKey().toLowerCase();
            getLoadBalancer(zone).setServersList(entry.getValue());
        }
    }    
    
    private Map<String, ZoneSnapshot> createSnapshot() {
        Map<String, ZoneSnapshot> map = new HashMap<String, ZoneSnapshot>();
        LoadBalancerStats lbStats = getLoadBalancerStats();
        for (String zone: lbStats.getAvailableZones()) {    
            ZoneSnapshot snapshot = lbStats.getZoneSnapshot(zone);
            map.put(zone, snapshot);
        }
        return map;
    }
    
    @Override
    public Server chooseServer(Object key) {
        Stopwatch stopWatch = chooseServerTimer.start();
        try {
            if (!ENABLED.get() || getLoadBalancerStats().getAvailableZones().size() <= 1) {
                logger.debug("Zone aware logic disabled or there is only one zone");
                return super.chooseServer(key);
            }
            Server server = null;
            try {
                LoadBalancerStats lbStats = getLoadBalancerStats();
                Map<String, ZoneSnapshot> zoneSnapshot = ZoneAvoidanceRule.createSnapshot(lbStats);
                logger.debug("Zone snapshots: {}", zoneSnapshot);
                if (triggeringLoad == null) {
                    triggeringLoad = DynamicPropertyFactory.getInstance().getDoubleProperty(
                        "ZoneAwareNIWSDiscoveryLoadBalancer." + this.getName() + ".triggeringLoadPerServerThreshold", 0.2d);
                }
                
                if (triggeringBlackoutPercentage == null) {
                    triggeringBlackoutPercentage = DynamicPropertyFactory.getInstance().getDoubleProperty(
                            "ZoneAwareNIWSDiscoveryLoadBalancer." + this.getName() + ".avoidZoneWithBlackoutPercetage", 0.99999d);
                }
                Set<String> availableZones = ZoneAvoidanceRule.getAvailableZones(zoneSnapshot, triggeringLoad.get(), triggeringBlackoutPercentage.get());
                logger.debug("Available zones: {}", availableZones);
                if (availableZones != null &&  availableZones.size() < zoneSnapshot.keySet().size()) {
                    String zone = ZoneAvoidanceRule.randomChooseZone(zoneSnapshot, availableZones);
                    logger.debug("Zone chosen: {}", zone);
                    if (zone != null) {
                        BaseLoadBalancer zoneLoadBalancer = getLoadBalancer(zone);
                        server = zoneLoadBalancer.chooseServer(key);
                    }
                }
            } catch (Throwable e) {
                logger.error("Unexpected exception when choosing server using zone aware logic", e);
            }
            if (server != null) {
                return server;
            } else {
                logger.debug("Zone avoidance logic is not invoked.");
                return super.chooseServer(key);
            }
        }  finally {
            stopWatch.stop();
        }
    }
        
    private BaseLoadBalancer getLoadBalancer(String zone) {
        zone = zone.toLowerCase();
        BaseLoadBalancer loadBalancer = balancers.get(zone);
        if (loadBalancer == null) {
            loadBalancer = new BaseLoadBalancer(this.getName() + "_" + zone, this.getRule(), this.getLoadBalancerStats());
            BaseLoadBalancer prev = balancers.putIfAbsent(zone, loadBalancer);
            if (prev != null) {
            	loadBalancer = prev;
            }
        } 
        return loadBalancer;
        
    }
    
    /**
     * Choose a zone from a list of zones based on number of instances
     * 
     * @param snapshot
     * @param chooseFrom
     * @return
     */
    private static String randomChooseZone(Map<String, ZoneSnapshot> snapshot, Set<String> chooseFrom) {
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
       
    private String chooseZone(Map<String, ZoneSnapshot> snapshot) {
        // make a copy, this might be changed later
        Set<String> availableZones = new HashSet<String>(snapshot.keySet());
        if (availableZones == null || availableZones.size() == 0) {
            return null;
        }
        if (availableZones.size() == 1) {
            return availableZones.iterator().next();
        }
        Set<String> worstZones = new HashSet<String>();
        double maxLoadPerServer = 0;
        boolean limitedZoneAvailability = false;
        if (triggeringLoad == null) {
            triggeringLoad = DynamicPropertyFactory.getInstance().getDoubleProperty(
                "ZoneAwareNIWSDiscoveryLoadBalancer." + this.getName() + ".triggeringLoadPerServerThreshold", 0.2d);
        }
        
        if (triggeringBlackoutPercentage == null) {
            triggeringBlackoutPercentage = DynamicPropertyFactory.getInstance().getDoubleProperty(
                    "ZoneAwareNIWSDiscoveryLoadBalancer." + this.getName() + ".avoidZoneWithBlackoutPercetage", 0.99999d);
        }

        for (Map.Entry<String, ZoneSnapshot> entry: snapshot.entrySet()) {
        	String zone = entry.getKey();
            ZoneSnapshot zoneSnapshot = entry.getValue();
            int instanceCount = zoneSnapshot.getInstanceCount();
            if (instanceCount == 0) {
                availableZones.remove(zone);
                limitedZoneAvailability = true;
            } else {
                double loadPerServer = zoneSnapshot.getLoadPerServer();
                if (((double) zoneSnapshot.getCircuitTrippedCount()) / instanceCount >= triggeringBlackoutPercentage.get()
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
                 
        if (maxLoadPerServer < triggeringLoad.get() && !limitedZoneAvailability) {
            // zone override is not needed here
            return null;
        }
                   
        String zoneToAvoid = randomChooseZone(snapshot, worstZones);
        if (zoneToAvoid != null) {
            availableZones.remove(zoneToAvoid);
        }
        return randomChooseZone(snapshot, availableZones);
    }    

    @Override
    public void setRule(IRule rule) {
        super.setRule(rule);
        if (balancers != null) {
            for (String zone: balancers.keySet()) {
                balancers.get(zone).setRule(rule);
            }
        }
    }
    
    @Override
    public void init() {
        chooseServerTimer = Monitors.newTimer("ZoneAwareLoadBalancer_" + getName() + "_chooseServerTimer", TimeUnit.MILLISECONDS);
        super.init();
    }
}
