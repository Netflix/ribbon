package com.netflix.client.config;

public class ClientConfigBuilder {
    
    private IClientConfig config;
    
    ClientConfigBuilder() {
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
    
    public static ClientConfigBuilder newBuilderWithArchaiusProperties(String clientName) {
        ClientConfigBuilder builder = new ClientConfigBuilder();
        builder.config = new DefaultClientConfigImpl();
        builder.config.loadProperties(clientName);
        return builder;
    }
    
    public static ClientConfigBuilder newBuilderWithArchaiusProperties(String clientName, String propertyNameSpace) {
        ClientConfigBuilder builder = new ClientConfigBuilder();
        builder.config = new DefaultClientConfigImpl(propertyNameSpace);
        builder.config.loadProperties(clientName);
        return builder;
    }

    
    public static ClientConfigBuilder newBuilderWithArchaiusProperties(Class<? extends DefaultClientConfigImpl> implClass, String clientName) {
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

    public ClientConfigBuilder withRetryOnAllOperations(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.OkToRetryOnAllOperations, value);
        return this;
    }

    public ClientConfigBuilder withRequestSpecificRetryOn(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.RequestSpecificRetryOn, value);
        return this;
    }
        
    public ClientConfigBuilder withEnablePrimeConnections(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.EnablePrimeConnections, value);
        return this;
    }

    public ClientConfigBuilder withMaxConnectionsPerHost(int value) {
        config.setPropertyWithType(CommonClientConfigKey.MaxHttpConnectionsPerHost, value);
        config.setPropertyWithType(CommonClientConfigKey.MaxConnectionsPerHost, value);
        return this;
    }

    public ClientConfigBuilder withMaxTotalConnections(int value) {
        config.setPropertyWithType(CommonClientConfigKey.MaxTotalHttpConnections, value);
        config.setPropertyWithType(CommonClientConfigKey.MaxTotalConnections, value);
        return this;
    }
    
    public ClientConfigBuilder withSecure(boolean secure) {
        config.setPropertyWithType(CommonClientConfigKey.IsSecure, secure);
        return this;
    }

    public ClientConfigBuilder withConnectTimeout(int value) {
        config.setPropertyWithType(CommonClientConfigKey.ConnectTimeout, value);
        return this;
    }

    public ClientConfigBuilder withReadTimeout(int value) {
        config.setPropertyWithType(CommonClientConfigKey.ReadTimeout, value);
        return this;
    }

    public ClientConfigBuilder withConnectionManagerTimeout(int value) {
        config.setPropertyWithType(CommonClientConfigKey.ConnectionManagerTimeout, value);
        return this;
    }
    
    public ClientConfigBuilder withFollowRedirects(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.FollowRedirects, value);
        return this;
    }
    
    public ClientConfigBuilder withConnectionPoolCleanerTaskEnabled(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled, value);
        return this;
    }
        
    public ClientConfigBuilder withConnIdleEvictTimeMilliSeconds(int value) {
        config.setPropertyWithType(CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds, value);
        return this;
    }
    
    public ClientConfigBuilder withConnectionCleanerRepeatIntervalMills(int value) {
        config.setPropertyWithType(CommonClientConfigKey.ConnectionCleanerRepeatInterval, value);
        return this;
    }
    
    public ClientConfigBuilder withGZIPContentEncodingFilterEnabled(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.EnableGZIPContentEncodingFilter, value);
        return this;
    }

    public ClientConfigBuilder withProxyHost(String proxyHost) {
        config.setPropertyWithType(CommonClientConfigKey.ProxyHost, proxyHost);
        return this;
    }

    public ClientConfigBuilder withProxyPort(int value) {
        config.setPropertyWithType(CommonClientConfigKey.ProxyPort, value);
        return this;
    }

    public ClientConfigBuilder withKeyStore(String value) {
        config.setPropertyWithType(CommonClientConfigKey.KeyStore, value);
        return this;
    }

    public ClientConfigBuilder withKeyStorePassword(String value) {
        config.setPropertyWithType(CommonClientConfigKey.KeyStorePassword, value);
        return this;
    }
    
    public ClientConfigBuilder withTrustStore(String value) {
        config.setPropertyWithType(CommonClientConfigKey.TrustStore, value);
        return this;
    }

    public ClientConfigBuilder withTrustStorePassword(String value) {
        config.setPropertyWithType(CommonClientConfigKey.TrustStorePassword, value);
        return this;
    }
    
    public ClientConfigBuilder withClientAuthRequired(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.IsClientAuthRequired, value);
        return this;
    }
    
    public ClientConfigBuilder withCustomSSLSocketFactoryClassName(String value) {
        config.setPropertyWithType(CommonClientConfigKey.CustomSSLSocketFactoryClassName, value);
        return this;
    }
    
    public ClientConfigBuilder withHostnameValidationRequired(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.IsHostnameValidationRequired, value);
        return this;
    }

    // see also http://hc.apache.org/httpcomponents-client-ga/tutorial/html/advanced.html
    public ClientConfigBuilder ignoreUserTokenInConnectionPoolForSecureClient(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.IgnoreUserTokenInConnectionPoolForSecureClient, value);
        return this;
    }

    public ClientConfigBuilder withInitializeNFLoadBalancer(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.InitializeNFLoadBalancer, value);
        return this;
    }
    
    public ClientConfigBuilder withServerListRefreshIntervalMills(int value) {
        config.setPropertyWithType(CommonClientConfigKey.ServerListRefreshInterval, value);
        return this;
    }
      
    public ClientConfigBuilder enableZoneAffinity(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.EnableZoneAffinity, value);
        return this;
    }
    
    public ClientConfigBuilder enableZoneExclusivity(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.EnableZoneExclusivity, value);
        return this;
    }

    public ClientConfigBuilder prioritizeVipAddressBasedServers(boolean value) {
        config.setPropertyWithType(CommonClientConfigKey.PrioritizeVipAddressBasedServers, value);
        return this;
    }
    
    public ClientConfigBuilder withTargetRegion(String value) {
        config.setPropertyWithType(CommonClientConfigKey.TargetRegion, value);
        return this;
    }
}
