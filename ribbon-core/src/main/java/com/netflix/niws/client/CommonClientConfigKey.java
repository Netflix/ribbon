package com.netflix.niws.client;

public enum CommonClientConfigKey implements IClientConfigKey {

    AppName("AppName"),
    Version("Version"),
    Port("Port"),
    SecurePort("SecurePort"),
    VipAddress("VipAddress"),
    DeploymentContextBasedVipAddresses("DeploymentContextBasedVipAddresses"),       
    MaxAutoRetries("MaxAutoRetries"),
    MaxAutoRetriesNextServer("MaxAutoRetriesNextServer"),
    OkToRetryOnAllOperations("OkToRetryOnAllOperations"),
    RequestSpecificRetryOn("RequestSpecificRetryOn"),
    ReceiveBuffferSize("ReceiveBuffferSize"),
    EnablePrimeConnections("EnablePrimeConnections"),
    PrimeConnectionsClassName("PrimeConnectionsClassName"),
    MaxRetriesPerServerPrimeConnection("MaxRetriesPerServerPrimeConnection"),
    MaxTotalTimeToPrimeConnections("MaxTotalTimeToPrimeConnections"),
    MinPrimeConnectionsRatio("MinPrimeConnectionsRatio"),
    PrimeConnectionsURI("PrimeConnectionsURI"),
    PoolMaxThreads("PoolMaxThreads"),
    PoolMinThreads("PoolMinThreads"),
    PoolKeepAliveTime("PoolKeepAliveTime"),
    PoolKeepAliveTimeUnits("PoolKeepAliveTimeUnits"),

    //HTTP Client Related
    MaxHttpConnectionsPerHost("MaxHttpConnectionsPerHost"),
    MaxTotalHttpConnections("MaxTotalHttpConnections"),
    IsSecure("IsSecure"),
    GZipPayload("GZipPayload"),
    ConnectTimeout("ConnectTimeout"),
    ReadTimeout("ReadTimeout"),
    SendBufferSize("SendBufferSize"),
    StaleCheckingEnabled("StaleCheckingEnabled"),
    Linger("Linger"),
    ConnectionManagerTimeout("ConnectionManagerTimeout"),
    FollowRedirects("FollowRedirects"),
    ConnectionPoolCleanerTaskEnabled("ConnectionPoolCleanerTaskEnabled"),
    ConnIdleEvictTimeMilliSeconds("ConnIdleEvictTimeMilliSeconds"),
    ConnectionCleanerRepeatInterval("ConnectionCleanerRepeatInterval"),
    EnableGZIPContentEncodingFilter("EnableGZIPContentEncodingFilter"),
    ProxyHost("ProxyHost"),
    ProxyPort("ProxyPort"),
    KeyStore("KeyStore"),
    KeyStorePassword("KeyStorePassword"),
    TrustStore("TrustStore"),
    TrustStorePassword("TrustStorePassword"),

    // Client implementation        
    ClientClassName("ClientClassName"),
    
    //LoadBalancer Related
    InitializeNFLoadBalancer("InitializeNFLoadBalancer"),
    NFLoadBalancerClassName("NFLoadBalancerClassName"),
    NFLoadBalancerRuleClassName("NFLoadBalancerRuleClassName"),
    NFLoadBalancerPingClassName("NFLoadBalancerPingClassName"),
    NFLoadBalancerPingInterval("NFLoadBalancerPingInterval"),
    NFLoadBalancerMaxTotalPingTime("NFLoadBalancerMaxTotalPingTime"),
    NIWSServerListClassName("NIWSServerListClassName"),
    NIWSServerListFilterClassName("NIWSServerListFilterClassName"),
    EnableMarkingServerDownOnReachingFailureLimit("EnableMarkingServerDownOnReachingFailureLimit"),
    ServerDownFailureLimit("ServerDownFailureLimit"),
    ServerDownStatWindowInMillis("ServerDownStatWindowInMillis"),
    EnableZoneAffinity("EnableZoneAffinity"),
    EnableZoneExclusivity("EnableZoneExclusivity"),
    PrioritizeVipAddressBasedServers("PrioritizeVipAddressBasedServers"),
    VipAddressResolverClassName("VipAddressResolverClassName");

    private final String configKey;

    CommonClientConfigKey(String configKey) {
        this.configKey = configKey;
    }

    /* (non-Javadoc)
	 * @see com.netflix.niws.client.ClientConfig#key()
	 */
    @Override
	public String key() {
        return configKey;
    }
}
