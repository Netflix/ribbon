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

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.niws.client.NiwsClientConfig.NiwsClientConfigKey;

/**
 * Class to hold a list of servers that NIWS RestClient can use
 * @author stonse
 *
 */
public class DiscoveryEnabledNIWSServerList extends AbstractNIWSServerList<DiscoveryEnabledServer>{

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
    public AbstractNIWSServerListFilter<DiscoveryEnabledServer> getFilterImpl(
            IClientConfig niwsClientConfig) throws NIWSClientException {
        try {
            String niwsServerListFilterClassName = niwsClientConfig
                    .getProperty(
                            CommonClientConfigKey.NIWSServerListFilterClassName,
                            DefaultNIWSServerListFilter.class.getName())
                    .toString();

            Class<AbstractNIWSServerListFilter<DiscoveryEnabledServer>> abstractNIWSServerListFilterClass = (Class<AbstractNIWSServerListFilter<DiscoveryEnabledServer>>) Class
                    .forName(niwsServerListFilterClassName);

            AbstractNIWSServerListFilter<DiscoveryEnabledServer> abstractNIWSServerListFilter = abstractNIWSServerListFilterClass.newInstance();
            if (abstractNIWSServerListFilter instanceof DefaultNIWSServerListFilter){
                abstractNIWSServerListFilter = (DefaultNIWSServerListFilter) abstractNIWSServerListFilter;
                ((DefaultNIWSServerListFilter) abstractNIWSServerListFilter).init(niwsClientConfig);
            }
            return abstractNIWSServerListFilter;
        } catch (Throwable e) {
            throw new NIWSClientException(
                    NIWSClientException.ErrorType.CONFIGURATION,
                    "Unable to get an instance of CommonClientConfigKey.NIWSServerListFilterClassName. Configured class:"
                            + niwsClientConfig
                                    .getProperty(CommonClientConfigKey.NIWSServerListFilterClassName));
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
