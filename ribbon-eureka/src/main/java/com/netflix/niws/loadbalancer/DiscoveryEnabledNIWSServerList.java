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
import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.AbstractServerListFilter;
import com.netflix.loadbalancer.ServerListFilter;

/**
 * Class to hold a list of servers that NIWS RestClient can use
 * @author stonse
 *
 */
public class DiscoveryEnabledNIWSServerList extends AbstractServerList<DiscoveryEnabledServer>{

    String clientName;
    String vipAddresses;
    boolean isSecure = false;
    
    boolean prioritizeVipAddressBasedServers = true;
  
    String datacenter;
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        this.clientName = clientConfig.getClientName();
        vipAddresses = clientConfig.resolveDeploymentContextbasedVipAddresses();
        isSecure = Boolean.parseBoolean(""+clientConfig.getProperty(CommonClientConfigKey.IsSecure, "false"));
        prioritizeVipAddressBasedServers = Boolean.parseBoolean(""+clientConfig.getProperty(CommonClientConfigKey.PrioritizeVipAddressBasedServers, prioritizeVipAddressBasedServers));
        
        datacenter = ConfigurationManager.getDeploymentContext().getDeploymentDatacenter();
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
                List<InstanceInfo> listOfinstanceInfo = discoveryClient
                .getInstancesByVipAddress(vipAddress, isSecure);
                for (InstanceInfo ii : listOfinstanceInfo) {
                    if (ii.getStatus().equals(InstanceStatus.UP)) {
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
    
    @Override
    public AbstractServerListFilter<DiscoveryEnabledServer> getFilterImpl(
            IClientConfig niwsClientConfig) throws ClientException {
        try {
            String niwsServerListFilterClassName = niwsClientConfig
                    .getProperty(
                            CommonClientConfigKey.NIWSServerListFilterClassName,
                            DefaultNIWSServerListFilter.class.getName())
                    .toString();

            AbstractServerListFilter<DiscoveryEnabledServer> abstractNIWSServerListFilter = 
            		(AbstractServerListFilter<DiscoveryEnabledServer>) ClientFactory.instantiateInstanceWithClientConfig(niwsServerListFilterClassName, niwsClientConfig);
            return abstractNIWSServerListFilter;
        } catch (Throwable e) {
            throw new ClientException(
                    ClientException.ErrorType.CONFIGURATION,
                    "Unable to get an instance of CommonClientConfigKey.NIWSServerListFilterClassName. Configured class:"
                            + niwsClientConfig
                                    .getProperty(CommonClientConfigKey.NIWSServerListFilterClassName), e);
        }

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
