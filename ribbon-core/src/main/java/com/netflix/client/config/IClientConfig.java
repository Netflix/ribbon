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

import java.util.Map;

/**
 * Defines the client configuration used by various APIs to initialize clients or load balancers
 * and for method execution. The default implementation is {@link DefaultClientConfigImpl}.
 * 
 * @author awang
 *
 */

public interface IClientConfig {
	
	public String getClientName();
		
	public String getNameSpace();

	/**
	 * Load the properties for a given client and/or load balancer. 
	 * @param clientName
	 */
	public void loadProperties(String clientName);
	
	/**
	 * load default values for this configuration
	 */
	public void loadDefaultValues();

	public Map<String, Object> getProperties();

    /**
     * @deprecated use {@link #set(IClientConfigKey, Object)} 
     */
	@Deprecated
	public void setProperty(IClientConfigKey key, Object value);

    /**
     * @deprecated use {@link #get(IClientConfigKey)}
     */
    @Deprecated
	public Object getProperty(IClientConfigKey key);

    /**
     * @deprecated use {@link #get(IClientConfigKey, Object)} 
     */
    @Deprecated
	public Object getProperty(IClientConfigKey key, Object defaultVal);

	public boolean containsProperty(IClientConfigKey key);
	
	/**
	 * Returns the applicable virtual addresses ("vip") used by this client configuration.
	 */
	public String resolveDeploymentContextbasedVipAddresses();
	
	public int getPropertyAsInteger(IClientConfigKey key, int defaultValue);

    public String getPropertyAsString(IClientConfigKey key, String defaultValue);
    
    public boolean getPropertyAsBoolean(IClientConfigKey key, boolean defaultValue);
    
    /**
     * Returns a typed property. If the property of IClientConfigKey is not set, it returns null.
     * <p>
     * For {@link DefaultClientConfigImpl}, if the value of the property is String, 
     * it will do basic type conversion from String to the following type:
     * <ul>
     * <li>Integer</li>
     * <li>Boolean</li>
     * <li>Float</li>
     * <li>Long</li>
     * <li>Double</li>
     * </ul>
     * <br><br>
     */
    public <T> T get(IClientConfigKey<T> key);
    
    /**
     * Returns a typed property. If the property of IClientConfigKey is not set, 
     * it returns the default value passed in as the parameter.
     */
    public <T> T get(IClientConfigKey<T> key, T defaultValue);

    /**
     * Set the typed property with the given value. 
     */
    public <T> IClientConfig set(IClientConfigKey<T> key, T value);
    
    public static class Builder {
        
        private IClientConfig config;
        
        Builder() {
        }
        
        /**
         * Create a builder with no initial property and value for the configuration to be built. The configuration object
         * that will be built is an instance of {@link DefaultClientConfigImpl}
         */
        public static Builder newBuilder() {
            Builder builder = new Builder();
            builder.config = new DefaultClientConfigImpl();
            return builder;
        }
        
        /**
         * Create a builder with properties for the specific client loaded. The default 
         * {@link IClientConfig} implementation loads properties from <a href="https://github.com/Netflix/archaius">Archaius</a>
         * 
         * @param clientName Name of client. clientName.ribbon will be used as a prefix to find corresponding properties from
         *      <a href="https://github.com/Netflix/archaius">Archaius</a>
         */
        public static Builder newBuilder(String clientName) {
            Builder builder = new Builder();
            builder.config = new DefaultClientConfigImpl();
            builder.config.loadProperties(clientName);
            return builder;
        }
        
        /**
         * Create a builder with properties for the specific client loaded. The default 
         * {@link IClientConfig} implementation loads properties from <a href="https://github.com/Netflix/archaius">Archaius</a>
         * 
         * @param clientName Name of client. clientName.propertyNameSpace will be used as a prefix to find corresponding properties from
         *      <a href="https://github.com/Netflix/archaius">Archaius</a>
         */
        public static Builder newBuilder(String clientName, String propertyNameSpace) {
            Builder builder = new Builder();
            builder.config = new DefaultClientConfigImpl(propertyNameSpace);
            builder.config.loadProperties(clientName);
            return builder;
        }

        
        /**
         * Create a builder with properties for the specific client loaded.
         * 
         *  @param implClass the class of {@link IClientConfig} object to be built
         */
        public static Builder newBuilder(Class<? extends IClientConfig> implClass, String clientName) {
            Builder builder = new Builder();
            try {
                builder.config = implClass.newInstance();
                builder.config.loadProperties(clientName);
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
            return builder;
        }

        /**
         * Create a builder to build the configuration with no initial properties set
         * 
         *  @param implClass the class of {@link IClientConfig} object to be built
         */
        public static Builder newBuilder(Class<? extends IClientConfig> implClass) {
            Builder builder = new Builder();
            try {
                builder.config = implClass.newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
            return builder;        
        }
        
        public IClientConfig build() {
            return config;
        }
        
        /**
         * Load a set of default values for the configuration
         */
        public Builder withDefaultValues() {
            config.loadDefaultValues();
            return this;
        }
        
        public Builder withDeploymentContextBasedVipAddresses(String vipAddress) {
            config.set(CommonClientConfigKey.DeploymentContextBasedVipAddresses, vipAddress);
            return this;
        }

        public Builder withForceClientPortConfiguration(boolean forceClientPortConfiguration) {
            config.set(CommonClientConfigKey.ForceClientPortConfiguration, forceClientPortConfiguration);
            return this;
        }

        public Builder withMaxAutoRetries(int value) {
            config.set(CommonClientConfigKey.MaxAutoRetries, value);
            return this;
        }

        public Builder withMaxAutoRetriesNextServer(int value) {
            config.set(CommonClientConfigKey.MaxAutoRetriesNextServer, value);
            return this;
        }

        public Builder withRetryOnAllOperations(boolean value) {
            config.set(CommonClientConfigKey.OkToRetryOnAllOperations, value);
            return this;
        }

        public Builder withRequestSpecificRetryOn(boolean value) {
            config.set(CommonClientConfigKey.RequestSpecificRetryOn, value);
            return this;
        }
            
        public Builder withEnablePrimeConnections(boolean value) {
            config.set(CommonClientConfigKey.EnablePrimeConnections, value);
            return this;
        }

        public Builder withMaxConnectionsPerHost(int value) {
            config.set(CommonClientConfigKey.MaxHttpConnectionsPerHost, value);
            config.set(CommonClientConfigKey.MaxConnectionsPerHost, value);
            return this;
        }

        public Builder withMaxTotalConnections(int value) {
            config.set(CommonClientConfigKey.MaxTotalHttpConnections, value);
            config.set(CommonClientConfigKey.MaxTotalConnections, value);
            return this;
        }
        
        public Builder withSecure(boolean secure) {
            config.set(CommonClientConfigKey.IsSecure, secure);
            return this;
        }

        public Builder withConnectTimeout(int value) {
            config.set(CommonClientConfigKey.ConnectTimeout, value);
            return this;
        }

        public Builder withReadTimeout(int value) {
            config.set(CommonClientConfigKey.ReadTimeout, value);
            return this;
        }

        public Builder withConnectionManagerTimeout(int value) {
            config.set(CommonClientConfigKey.ConnectionManagerTimeout, value);
            return this;
        }
        
        public Builder withFollowRedirects(boolean value) {
            config.set(CommonClientConfigKey.FollowRedirects, value);
            return this;
        }
        
        public Builder withConnectionPoolCleanerTaskEnabled(boolean value) {
            config.set(CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled, value);
            return this;
        }
            
        public Builder withConnIdleEvictTimeMilliSeconds(int value) {
            config.set(CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds, value);
            return this;
        }
        
        public Builder withConnectionCleanerRepeatIntervalMills(int value) {
            config.set(CommonClientConfigKey.ConnectionCleanerRepeatInterval, value);
            return this;
        }
        
        public Builder withGZIPContentEncodingFilterEnabled(boolean value) {
            config.set(CommonClientConfigKey.EnableGZIPContentEncodingFilter, value);
            return this;
        }

        public Builder withProxyHost(String proxyHost) {
            config.set(CommonClientConfigKey.ProxyHost, proxyHost);
            return this;
        }

        public Builder withProxyPort(int value) {
            config.set(CommonClientConfigKey.ProxyPort, value);
            return this;
        }

        public Builder withKeyStore(String value) {
            config.set(CommonClientConfigKey.KeyStore, value);
            return this;
        }

        public Builder withKeyStorePassword(String value) {
            config.set(CommonClientConfigKey.KeyStorePassword, value);
            return this;
        }
        
        public Builder withTrustStore(String value) {
            config.set(CommonClientConfigKey.TrustStore, value);
            return this;
        }

        public Builder withTrustStorePassword(String value) {
            config.set(CommonClientConfigKey.TrustStorePassword, value);
            return this;
        }
        
        public Builder withClientAuthRequired(boolean value) {
            config.set(CommonClientConfigKey.IsClientAuthRequired, value);
            return this;
        }
        
        public Builder withCustomSSLSocketFactoryClassName(String value) {
            config.set(CommonClientConfigKey.CustomSSLSocketFactoryClassName, value);
            return this;
        }
        
        public Builder withHostnameValidationRequired(boolean value) {
            config.set(CommonClientConfigKey.IsHostnameValidationRequired, value);
            return this;
        }

        // see also http://hc.apache.org/httpcomponents-client-ga/tutorial/html/advanced.html
        public Builder ignoreUserTokenInConnectionPoolForSecureClient(boolean value) {
            config.set(CommonClientConfigKey.IgnoreUserTokenInConnectionPoolForSecureClient, value);
            return this;
        }

        public Builder withLoadBalancerEnabled(boolean value) {
            config.set(CommonClientConfigKey.InitializeNFLoadBalancer, value);
            return this;
        }
        
        public Builder withServerListRefreshIntervalMills(int value) {
            config.set(CommonClientConfigKey.ServerListRefreshInterval, value);
            return this;
        }
        
        public Builder withZoneAffinityEnabled(boolean value) {
            config.set(CommonClientConfigKey.EnableZoneAffinity, value);
            return this;
        }
        
        public Builder withZoneExclusivityEnabled(boolean value) {
            config.set(CommonClientConfigKey.EnableZoneExclusivity, value);
            return this;
        }

        public Builder prioritizeVipAddressBasedServers(boolean value) {
            config.set(CommonClientConfigKey.PrioritizeVipAddressBasedServers, value);
            return this;
        }
        
        public Builder withTargetRegion(String value) {
            config.set(CommonClientConfigKey.TargetRegion, value);
            return this;
        }
    }

}
