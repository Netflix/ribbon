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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey.Keys;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.eureka1.utils.ServerListReader;
import com.netflix.eureka2.interests.Interests;
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
public class DiscoveryEnabledNIWSServerList extends AbstractServerList<DiscoveryEnabledServer> {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryEnabledNIWSServerList.class);

    String clientName;
    String vipAddresses;
    boolean isSecure = false;

    boolean prioritizeVipAddressBasedServers = true;

    String datacenter;
    String targetRegion;

    int overridePort = DefaultClientConfigImpl.DEFAULT_PORT;
    boolean shouldUseOverridePort = false;
    boolean shouldUseIpAddr = false;

    private AtomicReference<ServerListReader> serverListReaderRef = new AtomicReference<ServerListReader>();

    /**
     * @deprecated use {@link #DiscoveryEnabledNIWSServerList(String)}
     * or {@link #DiscoveryEnabledNIWSServerList(IClientConfig)}
     */
    @Deprecated
    public DiscoveryEnabledNIWSServerList() {
    }

    public DiscoveryEnabledNIWSServerList(String vipAddresses) {
        IClientConfig clientConfig = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
        clientConfig.set(Keys.DeploymentContextBasedVipAddresses, vipAddresses);
        initWithNiwsConfig(clientConfig);
    }

    public DiscoveryEnabledNIWSServerList(IClientConfig clientConfig) {
        initWithNiwsConfig(clientConfig);
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        clientName = clientConfig.getClientName();
        vipAddresses = clientConfig.resolveDeploymentContextbasedVipAddresses();
        if (vipAddresses == null &&
                ConfigurationManager.getConfigInstance().getBoolean("DiscoveryEnabledNIWSServerList.failFastOnNullVip", true)) {
            throw new NullPointerException("VIP address for client " + clientName + " is null");
        }
        isSecure = Boolean.parseBoolean("" + clientConfig.getProperty(CommonClientConfigKey.IsSecure, "false"));
        prioritizeVipAddressBasedServers = Boolean.parseBoolean("" + clientConfig.getProperty(CommonClientConfigKey.PrioritizeVipAddressBasedServers, prioritizeVipAddressBasedServers));
        datacenter = ConfigurationManager.getDeploymentContext().getDeploymentDatacenter();
        targetRegion = (String) clientConfig.getProperty(CommonClientConfigKey.TargetRegion);

        shouldUseIpAddr = clientConfig.getPropertyAsBoolean(CommonClientConfigKey.UseIPAddrForServer, DefaultClientConfigImpl.DEFAULT_USEIPADDRESS_FOR_SERVER);

        // override client configuration and use client-defined port
        if (clientConfig.getPropertyAsBoolean(CommonClientConfigKey.ForceClientPortConfiguration, false)) {

            if (isSecure) {

                if (clientConfig.containsProperty(CommonClientConfigKey.SecurePort)) {

                    overridePort = clientConfig.getPropertyAsInteger(CommonClientConfigKey.SecurePort, DefaultClientConfigImpl.DEFAULT_PORT);
                    shouldUseOverridePort = true;

                } else {
                    logger.warn(clientName + " set to force client port but no secure port is set, so ignoring");
                }
            } else {

                if (clientConfig.containsProperty(CommonClientConfigKey.Port)) {

                    overridePort = clientConfig.getPropertyAsInteger(CommonClientConfigKey.Port, DefaultClientConfigImpl.DEFAULT_PORT);
                    shouldUseOverridePort = true;

                } else {
                    logger.warn(clientName + " set to force client port but no port is set, so ignoring");
                }
            }
        }


    }

    @Override
    public List<DiscoveryEnabledServer> getInitialListOfServers() {
        return obtainServersViaDiscovery();
    }

    @Override
    public List<DiscoveryEnabledServer> getUpdatedListOfServers() {
        return obtainServersViaDiscovery();
    }

    private List<DiscoveryEnabledServer> obtainServersViaDiscovery() {
        if (ConfigurationManager.getConfigInstance().getBoolean("DiscoveryEnabledNIWSServerList.eureka2.enabled", false)) {
            return obtainServersViaEureka2();
        }
        return obtainServersViaEureka1();
    }

    private List<DiscoveryEnabledServer> obtainServersViaEureka1() {
        DiscoveryClient discoveryClient = DiscoveryManager.getInstance().getDiscoveryClient();
        if (discoveryClient == null) {
            return new ArrayList<DiscoveryEnabledServer>();
        }
        List<DiscoveryEnabledServer> serverList = null;
        if (vipAddresses != null) {
            for (String vipAddress : vipAddresses.split(",")) {
                // if targetRegion is null, it will be interpreted as the same region of client
                List<InstanceInfo> listOfinstanceInfo = discoveryClient.getInstancesByVipAddress(vipAddress, isSecure, targetRegion);
                serverList = toServerList(listOfinstanceInfo);
                if (!serverList.isEmpty() && prioritizeVipAddressBasedServers) {
                    break; // if the current vipAddress has servers, we dont use subsequent vipAddress based servers
                }
            }
        }
        return serverList == null ? Collections.<DiscoveryEnabledServer>emptyList() : serverList;
    }

    /**
     * TODO prioritizeVipAddressBasedServers not supported yet; all vips are subscribed to eagerly
     */
    private List<DiscoveryEnabledServer> obtainServersViaEureka2() {
        logger.info("Resolving {} from Eureka2...", this.vipAddresses);
        if (serverListReaderRef.get() == null) {
            String writeClusterHost = ConfigurationManager.getConfigInstance().getString("DiscoveryEnabledNIWSServerList.eureka2.writeCluster.host");
            int writeClusterPort = ConfigurationManager.getConfigInstance().getInt("DiscoveryEnabledNIWSServerList.eureka2.writeCluster.port");
            String readClusterVip = ConfigurationManager.getConfigInstance().getString("DiscoveryEnabledNIWSServerList.eureka2.readCluster.vip");

            ServerResolver serverResolver = ServerResolvers.fromEureka(
                    ServerResolvers.fromDnsName(writeClusterHost).withPort(writeClusterPort)
            ).forInterest(Interests.forVips(readClusterVip));

            String[] vipAddresses = this.vipAddresses.split(",");
            serverListReaderRef.compareAndSet(null, new ServerListReader(serverResolver, vipAddresses, isSecure));
        }
        List<InstanceInfo> instanceInfos = serverListReaderRef.get().getLatestServerListOrWait();
        if(instanceInfos == null) {
            logger.warn("Server resolve for vip={},secure={} timed out", this.vipAddresses, isSecure);
            return Collections.emptyList();
        }
        return toServerList(instanceInfos);
    }

    private List<DiscoveryEnabledServer> toServerList(List<InstanceInfo> listOfinstanceInfo) {
        List<DiscoveryEnabledServer> serverList = new ArrayList<>(listOfinstanceInfo.size());
        for (InstanceInfo ii : listOfinstanceInfo) {
            if (ii.getStatus().equals(InstanceStatus.UP)) {

                if (shouldUseOverridePort) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Overriding port on client name: " + clientName + " to " + overridePort);
                    }

                    // copy is necessary since the InstanceInfo builder just uses the original reference,
                    // and we don't want to corrupt the global eureka copy of the object which may be
                    // used by other clients in our system
                    InstanceInfo copy = new InstanceInfo(ii);

                    if (isSecure) {
                        ii = new InstanceInfo.Builder(copy).setSecurePort(overridePort).build();
                    } else {
                        ii = new InstanceInfo.Builder(copy).setPort(overridePort).build();
                    }
                }

                DiscoveryEnabledServer des = new DiscoveryEnabledServer(ii, isSecure, shouldUseIpAddr);
                des.setZone(DiscoveryClient.getZone(ii));
                serverList.add(des);
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

    public String toString() {
        StringBuilder sb = new StringBuilder("DiscoveryEnabledNIWSServerList:");
        sb.append("; clientName:").append(clientName);
        sb.append("; Effective vipAddresses:").append(vipAddresses);
        sb.append("; isSecure:").append(isSecure);
        sb.append("; datacenter:").append(datacenter);
        return sb.toString();
    }


}
