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
package com.netflix.client.config;


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
    IsClientAuthRequired("IsClientAuthRequired"), // if this is a secure rest client, must we use client auth too?
    IsHostnameValidationRequired("IsHostnameValidationRequired"), // must host name match name in certificate?
    IgnoreUserTokenInConnectionPoolForSecureClient("IgnoreUserTokenInConnectionPoolForSecureClient"), // see also http://hc.apache.org/httpcomponents-client-ga/tutorial/html/advanced.html

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
    ServerListRefreshInterval("ServerListRefreshInterval"),
    EnableMarkingServerDownOnReachingFailureLimit("EnableMarkingServerDownOnReachingFailureLimit"),
    ServerDownFailureLimit("ServerDownFailureLimit"),
    ServerDownStatWindowInMillis("ServerDownStatWindowInMillis"),
    EnableZoneAffinity("EnableZoneAffinity"),
    EnableZoneExclusivity("EnableZoneExclusivity"),
    PrioritizeVipAddressBasedServers("PrioritizeVipAddressBasedServers"),
    VipAddressResolverClassName("VipAddressResolverClassName"),
    TargetRegion("TargetRegion"),
    RulePredicateClasses("RulePredicateClasses");

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
