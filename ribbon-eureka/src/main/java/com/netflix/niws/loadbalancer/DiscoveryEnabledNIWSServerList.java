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
package com.netflix.niws.loadbalancer;

import java.util.ArrayList;
import java.util.List;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server list class that fetches the server information from Eureka client. ServerList is used by
 * {@link DynamicServerListLoadBalancer} to get server list dynamically. 
 * 
 * @author stonse
 *
 */
public class DiscoveryEnabledNIWSServerList extends AbstractServerList<DiscoveryEnabledServer>{

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryEnabledNIWSServerList.class);

    String clientName;
    String vipAddresses;
    boolean isSecure = false;
    
    boolean prioritizeVipAddressBasedServers = true;
  
    String datacenter;
    String targetRegion;

    int overridePort = DefaultClientConfigImpl.DEFAULT_PORT;
    boolean shouldUseOverridePort = false;
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        clientName = clientConfig.getClientName();
        vipAddresses = clientConfig.resolveDeploymentContextbasedVipAddresses();
        isSecure = Boolean.parseBoolean(""+clientConfig.getProperty(CommonClientConfigKey.IsSecure, "false"));
        prioritizeVipAddressBasedServers = Boolean.parseBoolean(""+clientConfig.getProperty(CommonClientConfigKey.PrioritizeVipAddressBasedServers, prioritizeVipAddressBasedServers));        
        datacenter = ConfigurationManager.getDeploymentContext().getDeploymentDatacenter();
        targetRegion = (String) clientConfig.getProperty(CommonClientConfigKey.TargetRegion);

        // override client configuration and use client-defined port
        if(clientConfig.getPropertyAsBoolean(CommonClientConfigKey.ForceClientPortConfiguration, false)){

            if(isSecure){

                if(clientConfig.containsProperty(CommonClientConfigKey.SecurePort)){

                    overridePort = clientConfig.getPropertyAsInteger(CommonClientConfigKey.SecurePort, DefaultClientConfigImpl.DEFAULT_PORT);
                    shouldUseOverridePort = true;

                }else{
                    logger.warn(clientName + " set to force client port but no secure port is set, so ignoring");
                }
            }else{

                if(clientConfig.containsProperty(CommonClientConfigKey.Port)){

                    overridePort = clientConfig.getPropertyAsInteger(CommonClientConfigKey.Port, DefaultClientConfigImpl.DEFAULT_PORT);
                    shouldUseOverridePort = true;

                }else{
                    logger.warn(clientName + " set to force client port but no port is set, so ignoring");
                }
            }
        }


    }
    
    
    @Override
    public List<DiscoveryEnabledServer> getInitialListOfServers(){
        return obtainServersViaDiscovery();
    }

   
    
    @Override
    public List<DiscoveryEnabledServer> getUpdatedListOfServers(){
        return obtainServersViaDiscovery();
    }
      
    private List<DiscoveryEnabledServer> obtainServersViaDiscovery() {
        List<DiscoveryEnabledServer> serverList = new ArrayList<DiscoveryEnabledServer>();
       
        DiscoveryClient discoveryClient = DiscoveryManager.getInstance()
                .getDiscoveryClient();
        if (discoveryClient == null) {
            return new ArrayList<DiscoveryEnabledServer>();
        }
        if (vipAddresses!=null){
            for (String vipAddress : vipAddresses.split(",")) {
                // if targetRegion is null, it will be interpreted as the same region of client
                List<InstanceInfo> listOfinstanceInfo = discoveryClient.getInstancesByVipAddress(vipAddress, isSecure, targetRegion); 
                for (InstanceInfo ii : listOfinstanceInfo) {
                    if (ii.getStatus().equals(InstanceStatus.UP)) {

                        if(shouldUseOverridePort){
                            if(logger.isDebugEnabled()){
                                logger.debug("Overriding port on client name: " + clientName + " to " + overridePort);
                            }

                            if(isSecure){
                                ii = new InstanceInfo.Builder(ii).setSecurePort(overridePort).build();
                            }else{
                                ii = new InstanceInfo.Builder(ii).setPort(overridePort).build();
                            }
                        }

                    	DiscoveryEnabledServer des = new DiscoveryEnabledServer(ii, isSecure);
                    	des.setZone(DiscoveryClient.getZone(ii));
                        serverList.add(des);
                    }
                }
                if (serverList.size()>0 && prioritizeVipAddressBasedServers){
                    break; // if the current vipAddress has servers, we dont use subsequent vipAddress based servers
                }
            }
        }
        return serverList;
    }

    public String getVipAddresses() {
        return vipAddresses;
    }

    public void setVipAddresses(String vipAddresses) {
        this.vipAddresses = vipAddresses;
    }
        
    public String toString(){
        StringBuilder sb = new StringBuilder("DiscoveryEnabledNIWSServerList:");
        sb.append("; clientName:").append(clientName);
        sb.append("; Effective vipAddresses:").append(vipAddresses);
        sb.append("; isSecure:").append(isSecure);
        sb.append("; datacenter:").append(datacenter);        
        return sb.toString();
    }

      
}
