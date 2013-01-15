package com.netflix.niws.client;


import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.loadbalancer.Server;

/**
 * Servers that were obtained via Discovery and hence contain
 * meta data in the form of InstanceInfo
 * @author stonse
 *
 */
public class DiscoveryEnabledServer extends Server{

    InstanceInfo instanceInfo;
    
    public DiscoveryEnabledServer(InstanceInfo instanceInfo, boolean useSecurePort) {    
    	super(instanceInfo.getHostName(), instanceInfo.getPort());
    	if(useSecurePort && instanceInfo.isPortEnabled(PortType.SECURE))
    		super.setPort(instanceInfo.getSecurePort());
        this.instanceInfo = instanceInfo;
    }
    
    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }
    
}
