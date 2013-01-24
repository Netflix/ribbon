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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext.ContextKey;
import com.netflix.config.DynamicDoubleProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.ZoneSnapshot;
import com.netflix.niws.client.IClientConfig;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;

/**
 * The Default NIWS Filter - deals with filtering out servers based on the Zone affinity and other related properties
 * @author stonse
 *
 */
public class DefaultNIWSServerListFilter extends
        AbstractNIWSServerListFilter<DiscoveryEnabledServer> {

    private volatile boolean zoneAffinity = DefaultClientConfigImpl.DEFAULT_ENABLE_ZONE_AFFINITY;
    private volatile boolean zoneExclusive = DefaultClientConfigImpl.DEFAULT_ENABLE_ZONE_EXCLUSIVITY;
    private DynamicDoubleProperty activeReqeustsPerServerThreshold;
    private DynamicDoubleProperty blackOutServerPercentageThreshold;
    private DynamicIntProperty availableServersThreshold;
    private Counter overrideCounter;
    
    private static Logger logger = LoggerFactory.getLogger(DefaultNIWSServerListFilter.class);
    
    String zone;
        
    public DefaultNIWSServerListFilter() {    	
    }
    
    public DefaultNIWSServerListFilter(IClientConfig niwsClientConfig) {
    	init(niwsClientConfig);
    }
    
    public void init(IClientConfig niwsClientConfig) {
        String sZoneAffinity = "" + niwsClientConfig.getProperty(CommonClientConfigKey.EnableZoneAffinity, false);
        if (sZoneAffinity != null){
            zoneAffinity = Boolean.parseBoolean(sZoneAffinity);
            logger.debug("ZoneAffinity is set to {}", zoneAffinity);
        }
        String sZoneExclusive = "" + niwsClientConfig.getProperty(CommonClientConfigKey.EnableZoneExclusivity, false);
        if (sZoneExclusive != null){
            zoneExclusive = Boolean.parseBoolean(sZoneExclusive);
        }
        if (ConfigurationManager.getDeploymentContext() != null) {
            zone = ConfigurationManager.getDeploymentContext().getValue(ContextKey.zone);
        }
        activeReqeustsPerServerThreshold = DynamicPropertyFactory.getInstance().getDoubleProperty(niwsClientConfig.getClientName() + "." + niwsClientConfig.getNameSpace() + ".zoneAffinity.maxLoadPerServer", 0.6d);
        logger.debug("activeReqeustsPerServerThreshold: {}", activeReqeustsPerServerThreshold.get());
        blackOutServerPercentageThreshold = DynamicPropertyFactory.getInstance().getDoubleProperty(niwsClientConfig.getClientName() + "." + niwsClientConfig.getNameSpace() + ".zoneAffinity.maxBlackOutServesrPercentage", 0.8d);
        logger.debug("blackOutServerPercentageThreshold: {}", blackOutServerPercentageThreshold.get());
        availableServersThreshold = DynamicPropertyFactory.getInstance().getIntProperty(niwsClientConfig.getClientName() + "." + niwsClientConfig.getNameSpace() + ".zoneAffinity.minAvailableServers", 2);
        logger.debug("availableServersThreshold: {}", availableServersThreshold.get());
        overrideCounter = Monitors.newCounter("ZoneAffinity_OverrideCounter");
        Monitors.registerObject("NIWSServerListFilter_" + niwsClientConfig.getClientName());
    }
    
    private boolean shouldEnableZoneAffinity(List<DiscoveryEnabledServer> filtered) {    
        if (!zoneAffinity && !zoneExclusive) {
            return false;
        }
        if (zoneExclusive) {
            return true;
        }
        LoadBalancerStats stats = getLoadBalancerStats();
        if (stats == null) {
            return zoneAffinity;
        } else {
            logger.debug("Determining if zone affinity should be enabled with given server list: {}", filtered);
            ZoneSnapshot snapshot = stats.getZoneSnapshot(filtered);
            double loadPerServer = snapshot.getLoadPerServer();
            int instanceCount = snapshot.getInstanceCount();            
            int circuitBreakerTrippedCount = snapshot.getCircuitTrippedCount();
            if (((double) circuitBreakerTrippedCount) / instanceCount >= blackOutServerPercentageThreshold.get() 
                    || loadPerServer >= activeReqeustsPerServerThreshold.get()
                    || (instanceCount - circuitBreakerTrippedCount) < availableServersThreshold.get()) {
                logger.debug("zoneAffinity is overriden. blackOutServerPercentage: {}, activeReqeustsPerServer: {}, availableServers: {}", 
                        new Object[] {(double) circuitBreakerTrippedCount / instanceCount,  loadPerServer, instanceCount - circuitBreakerTrippedCount});
                return false;
            } else {
                return true;
            }
            
        }
    }
        
    @Override
    public List<DiscoveryEnabledServer> getFilteredListOfServers(List<DiscoveryEnabledServer> servers) {
    	if ((zoneAffinity || zoneExclusive) && servers !=null && servers.size() > 0){
    		List<DiscoveryEnabledServer> filteredServers = new ArrayList<DiscoveryEnabledServer>();
    		if (zone != null) {
    			for (DiscoveryEnabledServer s : servers) {
    				if (DiscoveryEnabledServer.class.isAssignableFrom(s.getClass())) {
    					DiscoveryEnabledServer ds = (DiscoveryEnabledServer) s;
    					if (ds.getInstanceInfo() != null 
    							&& ds.getInstanceInfo().getDataCenterInfo() instanceof AmazonInfo) {
    						AmazonInfo ai = (AmazonInfo) ds.getInstanceInfo()
    								.getDataCenterInfo();
    						String az = ai.get(MetaDataKey.availabilityZone);
    						if (az != null && zone != null && az.toLowerCase().equals(zone.toLowerCase())) {
    							filteredServers.add(s);
    						}
    					}
    				}
    			}
    			if (shouldEnableZoneAffinity(filteredServers)) {
    				return filteredServers;
    			} else if (zoneAffinity) {
    				overrideCounter.increment();
    			}
    		}
    	}
    	return servers;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder("DefaultNIWSServerListFilter:");
        sb.append(", zone: ").append(zone).append(", zoneAffinity:").append(zoneAffinity);
        sb.append(", zoneExclusivity:").append(zoneExclusive);
        return sb.toString();
        
    }
        
        
}
