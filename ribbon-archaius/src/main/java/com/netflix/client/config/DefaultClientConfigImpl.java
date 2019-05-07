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

import com.netflix.client.VipAddressResolver;
import com.netflix.config.ConfigurationManager;
import org.apache.commons.configuration.event.ConfigurationEvent;
import org.apache.commons.configuration.event.ConfigurationListener;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
public class DefaultClientConfigImpl extends AbstractReloadableClientConfig {

    @Deprecated
    public static final Boolean DEFAULT_PRIORITIZE_VIP_ADDRESS_BASED_SERVERS = CommonClientConfigKey.PrioritizeVipAddressBasedServers.defaultValue();

    @Deprecated
    public static final String DEFAULT_NFLOADBALANCER_PING_CLASSNAME = CommonClientConfigKey.NFLoadBalancerPingClassName.defaultValue();

    @Deprecated
    public static final String DEFAULT_NFLOADBALANCER_RULE_CLASSNAME = CommonClientConfigKey.NFLoadBalancerRuleClassName.defaultValue();

    @Deprecated
    public static final String DEFAULT_NFLOADBALANCER_CLASSNAME = CommonClientConfigKey.NFLoadBalancerClassName.defaultValue();

    @Deprecated
    public static final boolean DEFAULT_USEIPADDRESS_FOR_SERVER = CommonClientConfigKey.UseIPAddrForServer.defaultValue();

    @Deprecated
    public static final String DEFAULT_CLIENT_CLASSNAME = CommonClientConfigKey.ClientClassName.defaultValue();

    @Deprecated
    public static final String DEFAULT_VIPADDRESS_RESOLVER_CLASSNAME = CommonClientConfigKey.VipAddressResolverClassName.defaultValue();

    @Deprecated
    public static final String DEFAULT_PRIME_CONNECTIONS_URI = CommonClientConfigKey.PrimeConnectionsURI.defaultValue();

    @Deprecated
    public static final int DEFAULT_MAX_TOTAL_TIME_TO_PRIME_CONNECTIONS = CommonClientConfigKey.MaxTotalTimeToPrimeConnections.defaultValue();

    @Deprecated
    public static final int DEFAULT_MAX_RETRIES_PER_SERVER_PRIME_CONNECTION = CommonClientConfigKey.MaxRetriesPerServerPrimeConnection.defaultValue();

    @Deprecated
    public static final Boolean DEFAULT_ENABLE_PRIME_CONNECTIONS = CommonClientConfigKey.EnablePrimeConnections.defaultValue();

    @Deprecated
    public static final int DEFAULT_MAX_REQUESTS_ALLOWED_PER_WINDOW = Integer.MAX_VALUE;

    @Deprecated
    public static final int DEFAULT_REQUEST_THROTTLING_WINDOW_IN_MILLIS = 60000;

    @Deprecated
    public static final Boolean DEFAULT_ENABLE_REQUEST_THROTTLING = Boolean.FALSE;

    @Deprecated
    public static final Boolean DEFAULT_ENABLE_GZIP_CONTENT_ENCODING_FILTER = CommonClientConfigKey.EnableGZIPContentEncodingFilter.defaultValue();

    @Deprecated
    public static final Boolean DEFAULT_CONNECTION_POOL_CLEANER_TASK_ENABLED = CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled.defaultValue();

    @Deprecated
    public static final Boolean DEFAULT_FOLLOW_REDIRECTS = CommonClientConfigKey.FollowRedirects.defaultValue();

    @Deprecated
    public static final float DEFAULT_PERCENTAGE_NIWS_EVENT_LOGGED = 0.0f;

    @Deprecated
    public static final int DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER = CommonClientConfigKey.MaxAutoRetriesNextServer.defaultValue();

    @Deprecated
    public static final int DEFAULT_MAX_AUTO_RETRIES = CommonClientConfigKey.MaxAutoRetries.defaultValue();

    @Deprecated
    public static final int DEFAULT_BACKOFF_INTERVAL = CommonClientConfigKey.BackoffInterval.defaultValue();

    @Deprecated
    public static final int DEFAULT_READ_TIMEOUT = CommonClientConfigKey.ReadTimeout.defaultValue();

    @Deprecated
    public static final int DEFAULT_CONNECTION_MANAGER_TIMEOUT = CommonClientConfigKey.ConnectionManagerTimeout.defaultValue();

    @Deprecated
    public static final int DEFAULT_CONNECT_TIMEOUT = CommonClientConfigKey.ConnectTimeout.defaultValue();

    @Deprecated
    public static final Boolean DEFAULT_ENABLE_CONNECTION_POOL = CommonClientConfigKey.EnableConnectionPool.defaultValue();

    @Deprecated
    public static final int DEFAULT_MAX_HTTP_CONNECTIONS_PER_HOST = CommonClientConfigKey.MaxHttpConnectionsPerHost.defaultValue();

    @Deprecated
    public static final int DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS = CommonClientConfigKey.MaxTotalHttpConnections.defaultValue();

    @Deprecated
    public static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = CommonClientConfigKey.MaxConnectionsPerHost.defaultValue();

    @Deprecated
    public static final int DEFAULT_MAX_TOTAL_CONNECTIONS = CommonClientConfigKey.MaxTotalConnections.defaultValue();

    @Deprecated
    public static final float DEFAULT_MIN_PRIME_CONNECTIONS_RATIO = CommonClientConfigKey.MinPrimeConnectionsRatio.defaultValue();

    @Deprecated
    public static final String DEFAULT_PRIME_CONNECTIONS_CLASS = CommonClientConfigKey.PrimeConnectionsClassName.defaultValue();

    @Deprecated
    public static final String DEFAULT_SEVER_LIST_CLASS = CommonClientConfigKey.NIWSServerListClassName.defaultValue();

    @Deprecated
    public static final String DEFAULT_SERVER_LIST_UPDATER_CLASS = CommonClientConfigKey.ServerListUpdaterClassName.defaultValue();

    @Deprecated
    public static final int DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS = CommonClientConfigKey.ConnectionCleanerRepeatInterval.defaultValue(); // every half minute (30 secs)

    @Deprecated
    public static final int DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS = CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds.defaultValue(); // all connections idle for 30 secs

    private VipAddressResolver resolver = null;

    /**
     * Defaults for the parameters for the thread pool used by batchParallel
     * calls
     */
    @Deprecated
    public static final int DEFAULT_POOL_MAX_THREADS = CommonClientConfigKey.MaxTotalHttpConnections.defaultValue();

    @Deprecated
    public static final int DEFAULT_POOL_MIN_THREADS = CommonClientConfigKey.PoolMinThreads.defaultValue();

    @Deprecated
    public static final long DEFAULT_POOL_KEEP_ALIVE_TIME = CommonClientConfigKey.PoolKeepAliveTime.defaultValue();

    @Deprecated
    public static final TimeUnit DEFAULT_POOL_KEEP_ALIVE_TIME_UNITS = TimeUnit.valueOf(CommonClientConfigKey.PoolKeepAliveTimeUnits.defaultValue());

    @Deprecated
    public static final Boolean DEFAULT_ENABLE_ZONE_AFFINITY = CommonClientConfigKey.EnableZoneAffinity.defaultValue();

    @Deprecated
    public static final Boolean DEFAULT_ENABLE_ZONE_EXCLUSIVITY = CommonClientConfigKey.EnableZoneExclusivity.defaultValue();

    @Deprecated
    public static final int DEFAULT_PORT = CommonClientConfigKey.Port.defaultValue();

    @Deprecated
    public static final Boolean DEFAULT_ENABLE_LOADBALANCER = CommonClientConfigKey.InitializeNFLoadBalancer.defaultValue();

    public static final String DEFAULT_PROPERTY_NAME_SPACE = CommonClientConfigKey.DEFAULT_NAME_SPACE;

    private String propertyNameSpace = DEFAULT_PROPERTY_NAME_SPACE;

    @Deprecated
    public static final Boolean DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS = CommonClientConfigKey.OkToRetryOnAllOperations.defaultValue();

    @Deprecated
    public static final Boolean DEFAULT_ENABLE_NIWS_EVENT_LOGGING = Boolean.TRUE;

    @Deprecated
    public static final Boolean DEFAULT_IS_CLIENT_AUTH_REQUIRED = Boolean.FALSE;

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

    public VipAddressResolver getResolver() {
        return resolver;
    }

    /**
     * Create instance with no properties in default name space {@link #DEFAULT_PROPERTY_NAME_SPACE}
     */
    public DefaultClientConfigImpl() {
        super();
    }

    /**
     * Create instance with no properties in the specified name space
     */
    public DefaultClientConfigImpl(String nameSpace) {
        this();
        this.propertyNameSpace = nameSpace;
    }

    public void loadDefaultValues() {
        super.loadDefaultValues();
        set(CommonClientConfigKey.MaxHttpConnectionsPerHost, getDefaultMaxHttpConnectionsPerHost());
        set(CommonClientConfigKey.MaxTotalHttpConnections, getDefaultMaxTotalHttpConnections());
        set(CommonClientConfigKey.EnableConnectionPool, getDefaultEnableConnectionPool());
        set(CommonClientConfigKey.MaxConnectionsPerHost, getDefaultMaxConnectionsPerHost());
        set(CommonClientConfigKey.MaxTotalConnections, getDefaultMaxTotalConnections());
        set(CommonClientConfigKey.ConnectTimeout, getDefaultConnectTimeout());
        set(CommonClientConfigKey.ConnectionManagerTimeout, getDefaultConnectionManagerTimeout());
        set(CommonClientConfigKey.ReadTimeout, getDefaultReadTimeout());
        set(CommonClientConfigKey.MaxAutoRetries, getDefaultMaxAutoRetries());
        set(CommonClientConfigKey.MaxAutoRetriesNextServer, getDefaultMaxAutoRetriesNextServer());
        set(CommonClientConfigKey.OkToRetryOnAllOperations, getDefaultOkToRetryOnAllOperations());
        set(CommonClientConfigKey.FollowRedirects, getDefaultFollowRedirects());
        set(CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled, getDefaultConnectionPoolCleanerTaskEnabled());
        set(CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds, getDefaultConnectionidleTimeInMsecs());
        set(CommonClientConfigKey.ConnectionCleanerRepeatInterval, getDefaultConnectionIdleTimertaskRepeatInMsecs());
        set(CommonClientConfigKey.EnableGZIPContentEncodingFilter, getDefaultEnableGzipContentEncodingFilter());
        String proxyHost = ConfigurationManager.getConfigInstance().getString(getDefaultPropName(CommonClientConfigKey.ProxyHost.key()));
        if (proxyHost != null && proxyHost.length() > 0) {
            set(CommonClientConfigKey.ProxyHost, proxyHost);
        }
        Integer proxyPort = ConfigurationManager
                .getConfigInstance()
                .getInteger(
                        getDefaultPropName(CommonClientConfigKey.ProxyPort),
                        (Integer.MIN_VALUE + 1)); // + 1 just to avoid potential clash with user setting
        if (proxyPort != (Integer.MIN_VALUE + 1)) {
            set(CommonClientConfigKey.ProxyPort, proxyPort);
        }
        set(CommonClientConfigKey.Port, getDefaultPort());
        set(CommonClientConfigKey.EnablePrimeConnections, getDefaultEnablePrimeConnections());
        set(CommonClientConfigKey.MaxRetriesPerServerPrimeConnection, getDefaultMaxRetriesPerServerPrimeConnection());
        set(CommonClientConfigKey.MaxTotalTimeToPrimeConnections, getDefaultMaxTotalTimeToPrimeConnections());
        set(CommonClientConfigKey.PrimeConnectionsURI, getDefaultPrimeConnectionsUri());
        set(CommonClientConfigKey.PoolMinThreads, getDefaultPoolMinThreads());
        set(CommonClientConfigKey.PoolMaxThreads, getDefaultPoolMaxThreads());
        set(CommonClientConfigKey.PoolKeepAliveTime, (int)getDefaultPoolKeepAliveTime());
        set(CommonClientConfigKey.PoolKeepAliveTimeUnits, getDefaultPoolKeepAliveTimeUnits().toString());
        set(CommonClientConfigKey.EnableZoneAffinity, getDefaultEnableZoneAffinity());
        set(CommonClientConfigKey.EnableZoneExclusivity, getDefaultEnableZoneExclusivity());
        set(CommonClientConfigKey.ClientClassName, getDefaultClientClassname());
        set(CommonClientConfigKey.NFLoadBalancerClassName, getDefaultNfloadbalancerClassname());
        set(CommonClientConfigKey.NFLoadBalancerRuleClassName, getDefaultNfloadbalancerRuleClassname());
        set(CommonClientConfigKey.NFLoadBalancerPingClassName, getDefaultNfloadbalancerPingClassname());
        set(CommonClientConfigKey.PrioritizeVipAddressBasedServers, getDefaultPrioritizeVipAddressBasedServers());
        set(CommonClientConfigKey.MinPrimeConnectionsRatio, getDefaultMinPrimeConnectionsRatio());
        set(CommonClientConfigKey.PrimeConnectionsClassName, getDefaultPrimeConnectionsClass());
        set(CommonClientConfigKey.NIWSServerListClassName, getDefaultSeverListClass());
        set(CommonClientConfigKey.VipAddressResolverClassName, getDefaultVipaddressResolverClassname());
        set(CommonClientConfigKey.IsClientAuthRequired, getDefaultIsClientAuthRequired());
        set(CommonClientConfigKey.UseIPAddrForServer, getDefaultUseIpAddressForServer());
        set(CommonClientConfigKey.ListOfServers, "");

        ConfigurationManager.getConfigInstance().addConfigurationListener(new ConfigurationListener() {
            @Override
            public void configurationChanged(ConfigurationEvent event) {
                if (!event.isBeforeUpdate()) {
                    reload();
                }
            }
        });
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

    /**
     * Load properties for a given client. It first loads the default values for all properties,
     * and any properties already defined with Archaius ConfigurationManager.
     */
    @Override
    public void loadProperties(String restClientName) {
        setClientName(restClientName);
        loadDefaultValues();
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "DC_DOUBLECHECK")
    private VipAddressResolver getVipAddressResolver() {
        if (resolver == null) {
            synchronized (this) {
                if (resolver == null) {
                    try {
                        resolver = (VipAddressResolver) Class
                                .forName(getOrDefault(CommonClientConfigKey.VipAddressResolverClassName))
                                .newInstance();
                    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                        throw new RuntimeException("Cannot instantiate VipAddressResolver", e);
                    }
                }
            }
        }
        return resolver;
    }

    @Override
    public String resolveDeploymentContextbasedVipAddresses(){
        String deploymentContextBasedVipAddressesMacro = get(CommonClientConfigKey.DeploymentContextBasedVipAddresses);
        if (deploymentContextBasedVipAddressesMacro == null) {
            return null;
        }
        return getVipAddressResolver().resolve(deploymentContextBasedVipAddressesMacro, this);
    }

    @Deprecated
    public String getAppName(){
        return get(CommonClientConfigKey.AppName);
    }

    @Deprecated
    public String getVersion(){
        return get(CommonClientConfigKey.Version);
    }

    @Override
    protected <T> Optional<T> loadProperty(String key, Class<T> type) {
        if (String.class.equals(type)) {
            return Optional.ofNullable(ConfigurationManager.getConfigInstance().getStringArray(key))
                    .filter(ar -> ar.length > 0)
                    .map(ar -> (T)Arrays.stream(ar).collect(Collectors.joining(",")));
        } else if (Integer.class.equals(type)) {
            return Optional.ofNullable((T) ConfigurationManager.getConfigInstance().getInteger(key, null));
        } else if (Boolean.class.equals(type)) {
            return Optional.ofNullable((T) ConfigurationManager.getConfigInstance().getBoolean(key, null));
        } else if (Float.class.equals(type)) {
            return Optional.ofNullable((T) ConfigurationManager.getConfigInstance().getFloat(key, null));
        } else if (Long.class.equals(type)) {
            return Optional.ofNullable((T) ConfigurationManager.getConfigInstance().getLong(key, null));
        } else if (Double.class.equals(type)) {
            return Optional.ofNullable((T) ConfigurationManager.getConfigInstance().getDouble(key, null));
        } else if (TimeUnit.class.equals(type)) {
            return Optional.ofNullable((T) TimeUnit.valueOf(ConfigurationManager.getConfigInstance().getString(key, null)));
        }

        throw new IllegalArgumentException("Unable to convert value to desired type " + type);
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