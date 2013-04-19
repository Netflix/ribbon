package com.netflix.niws.loadbalancer;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext.ContextKey;
import com.netflix.loadbalancer.AbstractServerPredicate;
import com.netflix.loadbalancer.Server;

public class ZoneAffinityPredicate extends AbstractServerPredicate {

    private static final String zone = ConfigurationManager.getDeploymentContext().getValue(ContextKey.zone);
    
    public ZoneAffinityPredicate() {        
    }

    @Override
    public boolean apply(Key input) {
        Server s = input.getServer();
        if (DiscoveryEnabledServer.class.isAssignableFrom(s.getClass())) {
            DiscoveryEnabledServer ds = (DiscoveryEnabledServer) s;
            if (ds.getInstanceInfo() != null 
                    && ds.getInstanceInfo().getDataCenterInfo() instanceof AmazonInfo) {
                AmazonInfo ai = (AmazonInfo) ds.getInstanceInfo()
                        .getDataCenterInfo();
                String az = ai.get(MetaDataKey.availabilityZone);
                if (az != null && zone != null && az.toLowerCase().equals(zone.toLowerCase())) {
                    return true;
                }
            } 
            return false;
        } else {
            return false;
        }
    }
}
