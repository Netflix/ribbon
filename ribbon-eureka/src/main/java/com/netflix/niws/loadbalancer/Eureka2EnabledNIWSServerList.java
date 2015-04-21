package com.netflix.niws.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.eureka1.utils.ServerListReader;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.loadbalancer.AbstractServerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class Eureka2EnabledNIWSServerList extends AbstractServerList<DiscoveryEnabledServer> {

    private static final Logger logger = LoggerFactory.getLogger(Eureka2EnabledNIWSServerList.class);

    public static final String EUREKA2_WRITE_CLUSTER_HOST = "eureka2.writeCluster.host";
    public static final CommonClientConfigKey<String> EUREKA2_WRITE_CLUSTER_HOST_KEY = new CommonClientConfigKey<String>(EUREKA2_WRITE_CLUSTER_HOST) {
    };

    public static final String EUREKA2_WRITE_CLUSTER_INTEREST_PORT = "eureka2.writeCluster.interestPort";
    public static final CommonClientConfigKey<Integer> EUREKA2_WRITE_CLUSTER_INTEREST_PORT_KEY = new CommonClientConfigKey<Integer>(EUREKA2_WRITE_CLUSTER_INTEREST_PORT) {
    };

    public static final String EUREKA2_READ_CLUSTER_VIP = "eureka2.readCluster.vip";
    public static final CommonClientConfigKey<String> EUREKA2_READ_CLUSTER_VIP_KEY = new CommonClientConfigKey<String>(EUREKA2_READ_CLUSTER_VIP) {
    };

    private IClientConfig clientConfig;

    private String clientName;
    private String vipAddresses;
    private boolean isSecure;

    private boolean prioritizeVipAddressBasedServers = true;

    private String datacenter;

    private int overridePort = DefaultClientConfigImpl.DEFAULT_PORT;
    private boolean shouldUseOverridePort;
    private boolean shouldUseIpAddr;

    private AtomicReference<ServerListReader> serverListReaderRef = new AtomicReference<ServerListReader>();

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        this.clientConfig = clientConfig;
        clientName = clientConfig.getClientName();
        vipAddresses = clientConfig.resolveDeploymentContextbasedVipAddresses();
        if (vipAddresses == null &&
                ConfigurationManager.getConfigInstance().getBoolean("DiscoveryEnabledNIWSServerList.failFastOnNullVip", true)) {
            throw new NullPointerException("VIP address for client " + clientName + " is null");
        }
        isSecure = clientConfig.getPropertyAsBoolean(CommonClientConfigKey.IsSecure, false);
        prioritizeVipAddressBasedServers = clientConfig.getPropertyAsBoolean(CommonClientConfigKey.PrioritizeVipAddressBasedServers, prioritizeVipAddressBasedServers);
        datacenter = ConfigurationManager.getDeploymentContext().getDeploymentDatacenter();

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
        return obtainServersViaEureka2();
    }

    @Override
    public List<DiscoveryEnabledServer> getUpdatedListOfServers() {
        return obtainServersViaEureka2();
    }

    /**
     * TODO prioritizeVipAddressBasedServers not supported yet; all vips are subscribed to eagerly
     */
    private List<DiscoveryEnabledServer> obtainServersViaEureka2() {
        logger.info("Resolving {} from Eureka2...", this.vipAddresses);
        List<InstanceInfo> instanceInfos = getServerListReader().getLatestServerListOrWait();
        if (instanceInfos == null) {
            logger.warn("Server resolve for vip={},secure={} timed out", this.vipAddresses, isSecure);
            return Collections.emptyList();
        }
        return toServerList(instanceInfos);
    }

    private ServerListReader getServerListReader() {
        if (serverListReaderRef.get() == null) {
            EurekaInterestClient interestClient;
            if (Eureka2Clients.getInterestClient() != null) {
                logger.info("Initializing Eureka2EnabledNIWSServerList with EurekaInterestClient provided by EurekaClients singleton");
                interestClient = Eureka2Clients.getInterestClient();
            } else {
                String writeClusterHost = clientConfig.getPropertyAsString(EUREKA2_WRITE_CLUSTER_HOST_KEY, null);
                int interestPort = clientConfig.getPropertyAsInteger(EUREKA2_WRITE_CLUSTER_INTEREST_PORT_KEY, EurekaTransports.DEFAULT_DISCOVERY_PORT);
                String readClusterVip = clientConfig.getPropertyAsString(EUREKA2_READ_CLUSTER_VIP_KEY, null);

                logger.info("Initializing Eureka2EnabledNIWSServerList from IClientConfig (writeClusterHost={}, interestPort={}, readClusterVip={})",
                        new Object[]{writeClusterHost, interestPort, readClusterVip});

                ServerResolver writeClusterResolver;
                if (writeClusterHost.indexOf('.') == -1) { // Simple host name
                    writeClusterResolver = ServerResolvers.from(new Server(writeClusterHost, interestPort));
                } else {
                    writeClusterResolver = ServerResolvers.fromDnsName(writeClusterHost).withPort(interestPort);
                }

                ServerResolver readClusterResolver = ServerResolvers
                        .fromEureka(writeClusterResolver)
                        .forInterest(Interests.forVips(readClusterVip));

                interestClient = Eurekas.newInterestClientBuilder().withServerResolver(readClusterResolver).build();
            }

            String[] vipAddresses = this.vipAddresses.split(",");
            serverListReaderRef.compareAndSet(null, new ServerListReader(interestClient, vipAddresses, isSecure));
        }
        return serverListReaderRef.get();
    }

    private List<DiscoveryEnabledServer> toServerList(List<InstanceInfo> listOfinstanceInfo) {
        List<DiscoveryEnabledServer> serverList = new ArrayList<>(listOfinstanceInfo.size());
        for (InstanceInfo ii : listOfinstanceInfo) {
            if (ii.getStatus() == InstanceStatus.UP) {
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
                des.setZone(resolveZone(ii));
                serverList.add(des);
            }
        }
        return serverList;
    }

    public String getVipAddresses() {
        return vipAddresses;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("DiscoveryEnabledNIWSServerList:");
        sb.append("; clientName:").append(clientName);
        sb.append("; Effective vipAddresses:").append(vipAddresses);
        sb.append("; isSecure:").append(isSecure);
        sb.append("; datacenter:").append(datacenter);
        return sb.toString();
    }

    /**
     * TODO implementation based on DiscoveryClient.getZone. Original implementation provides client default zone, if one not set explicitly.
     */
    private static String resolveZone(InstanceInfo myInfo) {
        String instanceZone = "default";
        if (myInfo != null && myInfo.getDataCenterInfo().getName() == Name.Amazon) {
            String awsInstanceZone = ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.availabilityZone);
            if (awsInstanceZone != null) {
                instanceZone = awsInstanceZone;
            }
        }
        return instanceZone;
    }
}
