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


import java.util.concurrent.TimeUnit;

/**
 * Default client configuration that loads properties from Archaius's ConfigurationManager.
 * <p>
 * The easiest way to configure client and load balancer is through loading properties into Archaius that conform to the specific format:

 <pre>{@code
<clientName>.<nameSpace>.<propertyName>=<value>
}</pre>
 <p>
 You can define properties in a file on classpath or as system properties. If former, ConfigurationManager.loadPropertiesFromResources() API should be called to load the file.
 <p>
 By default, "ribbon" should be the nameSpace.
 <p>
 If there is no property specified for a named client, {@code com.netflix.client.ClientFactory} will still create the client and
 load balancer with default values for all necessary properties. The default
 values are specified in this class as constants.
 <p>
 If a property is missing the clientName, it is interpreted as a property that applies to all clients. For example

 <pre>{@code
ribbon.ReadTimeout=1000
}</pre>

 This will establish the default ReadTimeout property for all clients.
 <p>
 You can also programmatically set properties by constructing instance of DefaultClientConfigImpl. Follow these steps:
 <ul>
 <li> Get an instance by calling {@link #getClientConfigWithDefaultValues(String)} to load default values,
 and any properties that are already defined with Configuration in Archaius
 <li> Set all desired properties by calling {@link #setProperty(IClientConfigKey, Object)} API.
 <li> Pass this instance together with client name to {@code com.netflix.client.ClientFactory} API.
 </ul>
 <p><p>
 If it is desired to have properties defined in a different name space, for example, "foo"

 <pre>{@code
myclient.foo.ReadTimeout=1000
}</pre>

 You should use {@link #getClientConfigWithDefaultValues(String, String)} - in the first step above.
 *
 * @author Sudhir Tonse
 * @author awang
 *
 */
public class DefaultClientConfigImpl extends AbstractDefaultClientConfigImpl {
    public static final String DEFAULT_PROPERTY_NAME_SPACE = CommonClientConfigKey.DEFAULT_NAME_SPACE;

    private String propertyNameSpace = DEFAULT_PROPERTY_NAME_SPACE;

    @Deprecated
    public Boolean getDefaultPrioritizeVipAddressBasedServers() {
        return DEFAULT_PRIORITIZE_VIP_ADDRESS_BASED_SERVERS;
    }

    @Deprecated
    public String getDefaultNfloadbalancerPingClassname() {
        return DEFAULT_NFLOADBALANCER_PING_CLASSNAME;
    }

    @Deprecated
    public String getDefaultNfloadbalancerRuleClassname() {
        return DEFAULT_NFLOADBALANCER_RULE_CLASSNAME;
    }

    @Deprecated
    public String getDefaultNfloadbalancerClassname() {
        return DEFAULT_NFLOADBALANCER_CLASSNAME;
    }

    @Deprecated
    public boolean getDefaultUseIpAddressForServer() {
        return DEFAULT_USEIPADDRESS_FOR_SERVER;
    }

    @Deprecated
    public String getDefaultClientClassname() {
        return DEFAULT_CLIENT_CLASSNAME;
    }

    @Deprecated
    public String getDefaultVipaddressResolverClassname() {
        return DEFAULT_VIPADDRESS_RESOLVER_CLASSNAME;
    }

    @Deprecated
    public String getDefaultPrimeConnectionsUri() {
        return DEFAULT_PRIME_CONNECTIONS_URI;
    }

    @Deprecated
    public int getDefaultMaxTotalTimeToPrimeConnections() {
        return DEFAULT_MAX_TOTAL_TIME_TO_PRIME_CONNECTIONS;
    }

    @Deprecated
    public int getDefaultMaxRetriesPerServerPrimeConnection() {
        return DEFAULT_MAX_RETRIES_PER_SERVER_PRIME_CONNECTION;
    }

    @Deprecated
    public Boolean getDefaultEnablePrimeConnections() {
        return DEFAULT_ENABLE_PRIME_CONNECTIONS;
    }

    @Deprecated
    public int getDefaultMaxRequestsAllowedPerWindow() {
        return DEFAULT_MAX_REQUESTS_ALLOWED_PER_WINDOW;
    }

    @Deprecated
    public int getDefaultRequestThrottlingWindowInMillis() {
        return DEFAULT_REQUEST_THROTTLING_WINDOW_IN_MILLIS;
    }

    @Deprecated
    public Boolean getDefaultEnableRequestThrottling() {
        return DEFAULT_ENABLE_REQUEST_THROTTLING;
    }

    @Deprecated
    public Boolean getDefaultEnableGzipContentEncodingFilter() {
        return DEFAULT_ENABLE_GZIP_CONTENT_ENCODING_FILTER;
    }

    @Deprecated
    public Boolean getDefaultConnectionPoolCleanerTaskEnabled() {
        return DEFAULT_CONNECTION_POOL_CLEANER_TASK_ENABLED;
    }

    @Deprecated
    public Boolean getDefaultFollowRedirects() {
        return DEFAULT_FOLLOW_REDIRECTS;
    }

    @Deprecated
    public float getDefaultPercentageNiwsEventLogged() {
        return DEFAULT_PERCENTAGE_NIWS_EVENT_LOGGED;
    }

    @Deprecated
    public int getDefaultMaxAutoRetriesNextServer() {
        return DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER;
    }

    @Deprecated
    public int getDefaultMaxAutoRetries() {
        return DEFAULT_MAX_AUTO_RETRIES;
    }

    @Deprecated
    public int getDefaultReadTimeout() {
        return DEFAULT_READ_TIMEOUT;
    }

    @Deprecated
    public int getDefaultConnectionManagerTimeout() {
        return DEFAULT_CONNECTION_MANAGER_TIMEOUT;
    }

    @Deprecated
    public int getDefaultConnectTimeout() {
        return DEFAULT_CONNECT_TIMEOUT;
    }

    @Deprecated
    public int getDefaultMaxHttpConnectionsPerHost() {
        return DEFAULT_MAX_HTTP_CONNECTIONS_PER_HOST;
    }

    @Deprecated
    public int getDefaultMaxTotalHttpConnections() {
        return DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS;
    }

    @Deprecated
    public int getDefaultMaxConnectionsPerHost() {
        return DEFAULT_MAX_CONNECTIONS_PER_HOST;
    }

    @Deprecated
    public int getDefaultMaxTotalConnections() {
        return DEFAULT_MAX_TOTAL_CONNECTIONS;
    }

    @Deprecated
    public float getDefaultMinPrimeConnectionsRatio() {
        return DEFAULT_MIN_PRIME_CONNECTIONS_RATIO;
    }

    @Deprecated
    public String getDefaultPrimeConnectionsClass() {
        return DEFAULT_PRIME_CONNECTIONS_CLASS;
    }

    @Deprecated
    public String getDefaultSeverListClass() {
        return DEFAULT_SEVER_LIST_CLASS;
    }

    @Deprecated
    public int getDefaultConnectionIdleTimertaskRepeatInMsecs() {
        return DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS;
    }

    @Deprecated
    public int getDefaultConnectionidleTimeInMsecs() {
        return DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS;
    }

    @Deprecated
    public int getDefaultPoolMaxThreads() {
        return DEFAULT_POOL_MAX_THREADS;
    }

    @Deprecated
    public int getDefaultPoolMinThreads() {
        return DEFAULT_POOL_MIN_THREADS;
    }

    @Deprecated
    public long getDefaultPoolKeepAliveTime() {
        return DEFAULT_POOL_KEEP_ALIVE_TIME;
    }

    @Deprecated
    public TimeUnit getDefaultPoolKeepAliveTimeUnits() {
        return DEFAULT_POOL_KEEP_ALIVE_TIME_UNITS;
    }

    @Deprecated
    public Boolean getDefaultEnableZoneAffinity() {
        return DEFAULT_ENABLE_ZONE_AFFINITY;
    }

    @Deprecated
    public Boolean getDefaultEnableZoneExclusivity() {
        return DEFAULT_ENABLE_ZONE_EXCLUSIVITY;
    }

    @Deprecated
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Deprecated
    public Boolean getDefaultEnableLoadbalancer() {
        return DEFAULT_ENABLE_LOADBALANCER;
    }

    @Deprecated
    public Boolean getDefaultOkToRetryOnAllOperations() {
        return DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS;
    }

    @Deprecated
    public Boolean getDefaultIsClientAuthRequired(){
        return DEFAULT_IS_CLIENT_AUTH_REQUIRED;
    }

    @Deprecated
    public Boolean getDefaultEnableConnectionPool() {
        return DEFAULT_ENABLE_CONNECTION_POOL;
    }

    /**
     * Create instance with no properties in default name space {@link #DEFAULT_PROPERTY_NAME_SPACE}
     */
    public DefaultClientConfigImpl() {
        super(ArchaiusPropertyResolver.INSTANCE);
    }

    /**
     * Create instance with no properties in the specified name space
     */
    public DefaultClientConfigImpl(String nameSpace) {
        this();
        this.propertyNameSpace = nameSpace;
    }

    public void loadDefaultValues() {
        setDefault(CommonClientConfigKey.MaxHttpConnectionsPerHost, getDefaultMaxHttpConnectionsPerHost());
        setDefault(CommonClientConfigKey.MaxTotalHttpConnections, getDefaultMaxTotalHttpConnections());
        setDefault(CommonClientConfigKey.EnableConnectionPool, getDefaultEnableConnectionPool());
        setDefault(CommonClientConfigKey.MaxConnectionsPerHost, getDefaultMaxConnectionsPerHost());
        setDefault(CommonClientConfigKey.MaxTotalConnections, getDefaultMaxTotalConnections());
        setDefault(CommonClientConfigKey.ConnectTimeout, getDefaultConnectTimeout());
        setDefault(CommonClientConfigKey.ConnectionManagerTimeout, getDefaultConnectionManagerTimeout());
        setDefault(CommonClientConfigKey.ReadTimeout, getDefaultReadTimeout());
        setDefault(CommonClientConfigKey.MaxAutoRetries, getDefaultMaxAutoRetries());
        setDefault(CommonClientConfigKey.MaxAutoRetriesNextServer, getDefaultMaxAutoRetriesNextServer());
        setDefault(CommonClientConfigKey.OkToRetryOnAllOperations, getDefaultOkToRetryOnAllOperations());
        setDefault(CommonClientConfigKey.FollowRedirects, getDefaultFollowRedirects());
        setDefault(CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled, getDefaultConnectionPoolCleanerTaskEnabled());
        setDefault(CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds, getDefaultConnectionidleTimeInMsecs());
        setDefault(CommonClientConfigKey.ConnectionCleanerRepeatInterval, getDefaultConnectionIdleTimertaskRepeatInMsecs());
        setDefault(CommonClientConfigKey.EnableGZIPContentEncodingFilter, getDefaultEnableGzipContentEncodingFilter());
        setDefault(CommonClientConfigKey.ProxyHost, null);
        setDefault(CommonClientConfigKey.ProxyPort, null);
        setDefault(CommonClientConfigKey.Port, getDefaultPort());
        setDefault(CommonClientConfigKey.EnablePrimeConnections, getDefaultEnablePrimeConnections());
        setDefault(CommonClientConfigKey.MaxRetriesPerServerPrimeConnection, getDefaultMaxRetriesPerServerPrimeConnection());
        setDefault(CommonClientConfigKey.MaxTotalTimeToPrimeConnections, getDefaultMaxTotalTimeToPrimeConnections());
        setDefault(CommonClientConfigKey.PrimeConnectionsURI, getDefaultPrimeConnectionsUri());
        setDefault(CommonClientConfigKey.PoolMinThreads, getDefaultPoolMinThreads());
        setDefault(CommonClientConfigKey.PoolMaxThreads, getDefaultPoolMaxThreads());
        setDefault(CommonClientConfigKey.PoolKeepAliveTime, (int)getDefaultPoolKeepAliveTime());
        setDefault(CommonClientConfigKey.PoolKeepAliveTimeUnits, getDefaultPoolKeepAliveTimeUnits().toString());
        setDefault(CommonClientConfigKey.EnableZoneAffinity, getDefaultEnableZoneAffinity());
        setDefault(CommonClientConfigKey.EnableZoneExclusivity, getDefaultEnableZoneExclusivity());
        setDefault(CommonClientConfigKey.ClientClassName, getDefaultClientClassname());
        setDefault(CommonClientConfigKey.NFLoadBalancerClassName, getDefaultNfloadbalancerClassname());
        setDefault(CommonClientConfigKey.NFLoadBalancerRuleClassName, getDefaultNfloadbalancerRuleClassname());
        setDefault(CommonClientConfigKey.NFLoadBalancerPingClassName, getDefaultNfloadbalancerPingClassname());
        setDefault(CommonClientConfigKey.PrioritizeVipAddressBasedServers, getDefaultPrioritizeVipAddressBasedServers());
        setDefault(CommonClientConfigKey.MinPrimeConnectionsRatio, getDefaultMinPrimeConnectionsRatio());
        setDefault(CommonClientConfigKey.PrimeConnectionsClassName, getDefaultPrimeConnectionsClass());
        setDefault(CommonClientConfigKey.NIWSServerListClassName, getDefaultSeverListClass());
        setDefault(CommonClientConfigKey.VipAddressResolverClassName, getDefaultVipaddressResolverClassname());
        setDefault(CommonClientConfigKey.IsClientAuthRequired, getDefaultIsClientAuthRequired());
        setDefault(CommonClientConfigKey.UseIPAddrForServer, getDefaultUseIpAddressForServer());
        setDefault(CommonClientConfigKey.ListOfServers, "");
    }

    @Deprecated
    protected void setPropertyInternal(IClientConfigKey propName, Object value) {
        set(propName, value);
    }

    // Helper methods which first check if a "default" (with rest client name)
    // property exists. If so, that value is used, else the default value
    // passed as argument is used to put into the properties member variable
    @Deprecated
    protected void putDefaultIntegerProperty(IClientConfigKey propName, Integer defaultValue) {
        this.set(propName, defaultValue);
    }

    @Deprecated
    protected void putDefaultLongProperty(IClientConfigKey propName, Long defaultValue) {
        this.set(propName, defaultValue);
    }

    @Deprecated
    protected void putDefaultFloatProperty(IClientConfigKey propName, Float defaultValue) {
        this.set(propName, defaultValue);
    }

    @Deprecated
    protected void putDefaultTimeUnitProperty(IClientConfigKey propName, TimeUnit defaultValue) {
        this.set(propName, defaultValue);
    }

    @Deprecated
    protected void putDefaultStringProperty(IClientConfigKey propName, String defaultValue) {
        this.set(propName, defaultValue);
    }

    @Deprecated
    protected void putDefaultBooleanProperty(IClientConfigKey propName, Boolean defaultValue) {
        this.set(propName, defaultValue);
    }

    @Deprecated
    String getDefaultPropName(String propName) {
        return getNameSpace() + "." + propName;
    }

    public String getDefaultPropName(IClientConfigKey propName) {
        return getDefaultPropName(propName.key());
    }

    public String getInstancePropName(String restClientName,
            IClientConfigKey configKey) {
        return getInstancePropName(restClientName, configKey.key());
    }

    public String getInstancePropName(String restClientName, String key) {
        if (getNameSpace() == null) {
            throw new NullPointerException("getNameSpace() may not be null");
        }
        return restClientName + "." + getNameSpace() + "." + key;
    }

    public DefaultClientConfigImpl withProperty(IClientConfigKey key, Object value) {
        setProperty(key, value);
        return this;
    }

    public static DefaultClientConfigImpl getEmptyConfig() {
        return new DefaultClientConfigImpl();
    }

    public static DefaultClientConfigImpl getClientConfigWithDefaultValues(String clientName) {
        return getClientConfigWithDefaultValues(clientName, DEFAULT_PROPERTY_NAME_SPACE);
    }

    public static DefaultClientConfigImpl getClientConfigWithDefaultValues() {
        return getClientConfigWithDefaultValues("default", DEFAULT_PROPERTY_NAME_SPACE);
    }

    public static DefaultClientConfigImpl getClientConfigWithDefaultValues(String clientName, String nameSpace) {
        DefaultClientConfigImpl config = new DefaultClientConfigImpl(nameSpace);
        config.loadProperties(clientName);
        return config;
    }
}