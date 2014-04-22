package com.netflix.client.config;

public class ClientConfigBuilder {
    
    private IClientConfig config;
    
    private ClientConfigBuilder() {
    }
    
    public static ClientConfigBuilder newBuilder() {
        ClientConfigBuilder builder = new ClientConfigBuilder();
        builder.config = new DefaultClientConfigImpl();
        return builder;
    }
    
    public static ClientConfigBuilder newBuilderWithDefaultConfigValues() {
        ClientConfigBuilder builder = new ClientConfigBuilder();
        builder.config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
        return builder;
    }
    
    public static ClientConfigBuilder newBuilderWithPropertiesForClient(String clientName) {
        ClientConfigBuilder builder = new ClientConfigBuilder();
        builder.config = new DefaultClientConfigImpl();
        builder.config.loadProperties(clientName);
        return builder;
    }
    
    public static ClientConfigBuilder newBuilderWithPropertiesForClient(String clientName, String nameSpace) {
        ClientConfigBuilder builder = new ClientConfigBuilder();
        builder.config = new DefaultClientConfigImpl(nameSpace);
        builder.config.loadProperties(clientName);
        return builder;
    }

    
    public static ClientConfigBuilder newBuilderWithPropertiesForClient(Class<? extends IClientConfig> implClass, String clientName) {
        ClientConfigBuilder builder = new ClientConfigBuilder();
        try {
            builder.config = implClass.newInstance();
            builder.config.loadProperties(clientName);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return builder;
    }

    public static ClientConfigBuilder newBuilder(Class<? extends IClientConfig> implClass) {
        ClientConfigBuilder builder = new ClientConfigBuilder();
        try {
            builder.config = implClass.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return builder;        
    }
    
    public static ClientConfigBuilder newBuilderWithDefaultConfigValues(Class<? extends DefaultClientConfigImpl> implClass) {
        ClientConfigBuilder builder = newBuilder(implClass);
        ((DefaultClientConfigImpl) builder.config).loadDefaultValues();
        return builder;
    }

    public IClientConfig build() {
        return config;
    }
    
    public ClientConfigBuilder withAppName(String appName) {
        config.setPropertyWithType(CommonClientConfigKey.AppName, appName);
        return this;
    }
    
    public ClientConfigBuilder withVersion(String version) {
        config.setPropertyWithType(CommonClientConfigKey.Version, version);
        return this;
    }
        
    public ClientConfigBuilder withPort(int port) {
        config.setPropertyWithType(CommonClientConfigKey.Port, port);
        return this;
    }
    
    public ClientConfigBuilder withSecurePort(int port) {
        config.setPropertyWithType(CommonClientConfigKey.SecurePort, port);
        return this;
    }
    
    public ClientConfigBuilder withDeploymentContextBasedVipAddresses(String vipAddress) {
        config.setPropertyWithType(CommonClientConfigKey.DeploymentContextBasedVipAddresses, vipAddress);
        return this;
    }

    public ClientConfigBuilder withForceClientPortConfiguration(boolean forceClientPortConfiguration) {
        config.setPropertyWithType(CommonClientConfigKey.ForceClientPortConfiguration, forceClientPortConfiguration);
        return this;
    }

    public ClientConfigBuilder withMaxAutoRetries(int value) {
        config.setPropertyWithType(CommonClientConfigKey.MaxAutoRetries, value);
        return this;
    }

    public ClientConfigBuilder withMaxAutoRetriesNextServer(int value) {
        config.setPropertyWithType(CommonClientConfigKey.MaxAutoRetriesNextServer, value);
        return this;
    }

    public ClientConfigBuilder withOkToRetryOnAllOperations(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.OkToRetryOnAllOperations, value);
        return this;
    }

    public ClientConfigBuilder withRequestSpecificRetryOn(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.RequestSpecificRetryOn, value);
        return this;
    }
        
    public ClientConfigBuilder withReceiveBuffferSize(int value) {
        config.setPropertyWithType(CommonClientConfigKey.ReceiveBuffferSize, value);
        return this;
    }

    public static final IClientConfigKey<Integer> ReceiveBuffferSize = new CommonClientConfigKey<Integer>("ReceiveBuffferSize"){};
    
    public static final IClientConfigKey<Boolean> EnablePrimeConnections = new CommonClientConfigKey<Boolean>("EnablePrimeConnections"){};
    
    public static final IClientConfigKey<Boolean> PrimeConnectionsClassName = new CommonClientConfigKey<Boolean>("PrimeConnectionsClassName"){};
    
    public static final IClientConfigKey<Integer> MaxRetriesPerServerPrimeConnection = new CommonClientConfigKey<Integer>("MaxRetriesPerServerPrimeConnection"){};
    
    public static final IClientConfigKey<Integer> MaxTotalTimeToPrimeConnections = new CommonClientConfigKey<Integer>("MaxTotalTimeToPrimeConnections"){};
    
    public static final IClientConfigKey<Float> MinPrimeConnectionsRatio = new CommonClientConfigKey<Float>("MinPrimeConnectionsRatio"){};
    
    public static final IClientConfigKey<String> PrimeConnectionsURI = new CommonClientConfigKey<String>("PrimeConnectionsURI"){};
    
    public static final IClientConfigKey<Integer> PoolMaxThreads = new CommonClientConfigKey<Integer>("PoolMaxThreads"){};
    
    public static final IClientConfigKey<Integer> PoolMinThreads = new CommonClientConfigKey<Integer>("PoolMinThreads"){};
    
    public static final IClientConfigKey<Integer> PoolKeepAliveTime = new CommonClientConfigKey<Integer>("PoolKeepAliveTime"){};
    
    public static final IClientConfigKey<String> PoolKeepAliveTimeUnits = new CommonClientConfigKey<String>("PoolKeepAliveTimeUnits"){};

    //HTTP Client Related
    public static final IClientConfigKey<Integer> MaxHttpConnectionsPerHost = new CommonClientConfigKey<Integer>("MaxHttpConnectionsPerHost"){};
    
    public static final IClientConfigKey<Integer> MaxTotalHttpConnections = new CommonClientConfigKey<Integer>("MaxTotalHttpConnections"){};
    
    public static final IClientConfigKey<Boolean> IsSecure = new CommonClientConfigKey<Boolean>("IsSecure"){};
    
    public static final IClientConfigKey<Boolean> GZipPayload = new CommonClientConfigKey<Boolean>("GZipPayload"){};
    
    public static final IClientConfigKey<Integer> ConnectTimeout = new CommonClientConfigKey<Integer>("ConnectTimeout"){};
    
    public static final IClientConfigKey<Integer> ReadTimeout = new CommonClientConfigKey<Integer>("ReadTimeout"){};
    
    public static final IClientConfigKey<Integer> SendBufferSize = new CommonClientConfigKey<Integer>("SendBufferSize"){};
    
    public static final IClientConfigKey<Boolean> StaleCheckingEnabled = new CommonClientConfigKey<Boolean>("StaleCheckingEnabled"){};
    
    public static final IClientConfigKey<Integer> Linger = new CommonClientConfigKey<Integer>("Linger"){};
    
    public static final IClientConfigKey<Integer> ConnectionManagerTimeout = new CommonClientConfigKey<Integer>("ConnectionManagerTimeout"){};
    
    public static final IClientConfigKey<Boolean> FollowRedirects = new CommonClientConfigKey<Boolean>("FollowRedirects"){};
    
    public static final IClientConfigKey<Boolean> ConnectionPoolCleanerTaskEnabled = new CommonClientConfigKey<Boolean>("ConnectionPoolCleanerTaskEnabled"){};
    
    public static final IClientConfigKey<Integer> ConnIdleEvictTimeMilliSeconds = new CommonClientConfigKey<Integer>("ConnIdleEvictTimeMilliSeconds"){};
    
    public static final IClientConfigKey<Integer> ConnectionCleanerRepeatInterval = new CommonClientConfigKey<Integer>("ConnectionCleanerRepeatInterval"){};
    
    public static final IClientConfigKey<Boolean> EnableGZIPContentEncodingFilter = new CommonClientConfigKey<Boolean>("EnableGZIPContentEncodingFilter"){};
    
    public static final IClientConfigKey<String> ProxyHost = new CommonClientConfigKey<String>("ProxyHost"){};
    
    public static final IClientConfigKey<Integer> ProxyPort = new CommonClientConfigKey<Integer>("ProxyPort"){};
    
    public static final IClientConfigKey<String> KeyStore = new CommonClientConfigKey<String>("KeyStore"){};
    
    public static final IClientConfigKey<String> KeyStorePassword = new CommonClientConfigKey<String>("KeyStorePassword"){};
    
    public static final IClientConfigKey<String> TrustStore = new CommonClientConfigKey<String>("TrustStore"){};
    
    public static final IClientConfigKey<String> TrustStorePassword = new CommonClientConfigKey<String>("TrustStorePassword"){};
    
    // if this is a secure rest client, must we use client auth too?    
    public static final IClientConfigKey<Boolean> IsClientAuthRequired = new CommonClientConfigKey<Boolean>("IsClientAuthRequired"){};
    
    public static final IClientConfigKey<String> CustomSSLSocketFactoryClassName = new CommonClientConfigKey<String>("CustomSSLSocketFactoryClassName"){};
     // must host name match name in certificate?
    public static final IClientConfigKey<Boolean> IsHostnameValidationRequired = new CommonClientConfigKey<Boolean>("IsHostnameValidationRequired"){}; 

    // see also http://hc.apache.org/httpcomponents-client-ga/tutorial/html/advanced.html
    public static final IClientConfigKey<Boolean> IgnoreUserTokenInConnectionPoolForSecureClient = new CommonClientConfigKey<Boolean>("IgnoreUserTokenInConnectionPoolForSecureClient"){}; 
    
    // Client implementation
    public static final IClientConfigKey<String> ClientClassName = new CommonClientConfigKey<String>("ClientClassName"){};

    //LoadBalancer Related
    public static final IClientConfigKey<Boolean> InitializeNFLoadBalancer = new CommonClientConfigKey<Boolean>("InitializeNFLoadBalancer"){};
    
    public static final IClientConfigKey<String> NFLoadBalancerClassName = new CommonClientConfigKey<String>("NFLoadBalancerClassName"){};
    
    public static final IClientConfigKey<String> NFLoadBalancerRuleClassName = new CommonClientConfigKey<String>("NFLoadBalancerRuleClassName"){};
    
    public static final IClientConfigKey<String> NFLoadBalancerPingClassName = new CommonClientConfigKey<String>("NFLoadBalancerPingClassName"){};
    
    public static final IClientConfigKey<Integer> NFLoadBalancerPingInterval = new CommonClientConfigKey<Integer>("NFLoadBalancerPingInterval"){};
    
    public static final IClientConfigKey<Integer> NFLoadBalancerMaxTotalPingTime = new CommonClientConfigKey<Integer>("NFLoadBalancerMaxTotalPingTime"){};
    
    public static final IClientConfigKey<String> NIWSServerListClassName = new CommonClientConfigKey<String>("NIWSServerListClassName"){};
    
    public static final IClientConfigKey<String> NIWSServerListFilterClassName = new CommonClientConfigKey<String>("NIWSServerListFilterClassName"){};
    
    public static final IClientConfigKey<Integer> ServerListRefreshInterval = new CommonClientConfigKey<Integer>("ServerListRefreshInterval"){};
    
    public static final IClientConfigKey<Boolean> EnableMarkingServerDownOnReachingFailureLimit = new CommonClientConfigKey<Boolean>("EnableMarkingServerDownOnReachingFailureLimit"){};
    
    public static final IClientConfigKey<Integer> ServerDownFailureLimit = new CommonClientConfigKey<Integer>("ServerDownFailureLimit"){};
    
    public static final IClientConfigKey<Integer> ServerDownStatWindowInMillis = new CommonClientConfigKey<Integer>("ServerDownStatWindowInMillis"){};
    
    public static final IClientConfigKey<Boolean> EnableZoneAffinity = new CommonClientConfigKey<Boolean>("EnableZoneAffinity"){};
    
    public static final IClientConfigKey<Boolean> EnableZoneExclusivity = new CommonClientConfigKey<Boolean>("EnableZoneExclusivity"){};
    
    public static final IClientConfigKey<Boolean> PrioritizeVipAddressBasedServers = new CommonClientConfigKey<Boolean>("PrioritizeVipAddressBasedServers"){};
    
    public static final IClientConfigKey<String> VipAddressResolverClassName = new CommonClientConfigKey<String>("VipAddressResolverClassName"){};
    
    public static final IClientConfigKey<String> TargetRegion = new CommonClientConfigKey<String>("TargetRegion"){};
    
    public static final IClientConfigKey<String> RulePredicateClasses = new CommonClientConfigKey<String>("RulePredicateClasses"){};
    
    
}
