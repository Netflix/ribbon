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

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.netflix.client.ClientFactory;
import com.netflix.client.VipAddressResolver;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;

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
If there is no property specified for a named client, {@link ClientFactory} will still create the client and
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

<li> Get an instance by calling {@link #getClientConfigWithDefaultValues(String)} to load default values,
and any properties that are already defined with Configuration in Archaius
<li> Set all desired properties by calling {@link #setProperty(IClientConfigKey, Object)} API.
<li> Pass this instance together with client name to {@link ClientFactory} API.
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
public class DefaultClientConfigImpl implements IClientConfig {

    public static final Boolean DEFAULT_PRIORITIZE_VIP_ADDRESS_BASED_SERVERS = Boolean.TRUE;

	public static final String DEFAULT_NFLOADBALANCER_PING_CLASSNAME = "com.netflix.loadbalancer.DummyPing"; // DummyPing.class.getName();

    public static final String DEFAULT_NFLOADBALANCER_RULE_CLASSNAME = "com.netflix.loadbalancer.AvailabilityFilteringRule";

    public static final String DEFAULT_NFLOADBALANCER_CLASSNAME = "com.netflix.loadbalancer.ZoneAwareLoadBalancer";

    public static final String DEFAULT_CLIENT_CLASSNAME = "com.netflix.niws.client.http.RestClient";

    public static final String DEFAULT_VIPADDRESS_RESOLVER_CLASSNAME = "com.netflix.client.SimpleVipAddressResolver";

    public static final String DEFAULT_PRIME_CONNECTIONS_URI = "/";

    public static final int DEFAULT_MAX_TOTAL_TIME_TO_PRIME_CONNECTIONS = 30000;

    public static final int DEFAULT_MAX_RETRIES_PER_SERVER_PRIME_CONNECTION = 9;

    public static final Boolean DEFAULT_ENABLE_PRIME_CONNECTIONS = Boolean.FALSE;

    public static final int DEFAULT_MAX_REQUESTS_ALLOWED_PER_WINDOW = Integer.MAX_VALUE;

    public static final int DEFAULT_REQUEST_THROTTLING_WINDOW_IN_MILLIS = 60000;

    public static final Boolean DEFAULT_ENABLE_REQUEST_THROTTLING = Boolean.FALSE;

    public static final Boolean DEFAULT_ENABLE_GZIP_CONTENT_ENCODING_FILTER = Boolean.FALSE;

    public static final Boolean DEFAULT_CONNECTION_POOL_CLEANER_TASK_ENABLED = Boolean.TRUE;

    public static final Boolean DEFAULT_FOLLOW_REDIRECTS = Boolean.TRUE;

    public static final float DEFAULT_PERCENTAGE_NIWS_EVENT_LOGGED = 0.0f;

    public static final int DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER = 0;

    public static final int DEFAULT_MAX_AUTO_RETRIES = 0;

    public static final int DEFAULT_READ_TIMEOUT = 5000;

    public static final int DEFAULT_CONNECTION_MANAGER_TIMEOUT = 2000;

    public static final int DEFAULT_CONNECT_TIMEOUT = 2000;

    public static final int DEFAULT_MAX_HTTP_CONNECTIONS_PER_HOST = 50;

    public static final int DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS = 200;


    public static final float DEFAULT_MIN_PRIME_CONNECTIONS_RATIO = 1.0f;

    public static final String DEFAULT_PRIME_CONNECTIONS_CLASS = "com.netflix.niws.client.http.HttpPrimeConnection";

    public static final String DEFAULT_SEVER_LIST_CLASS = "com.netflix.loadbalancer.ConfigurationBasedServerList";

    public static final int DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS = 30000; // every half minute (30 secs)

    public static final int DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS = 30000; // all connections idle for 30 secs

    protected volatile Map<String, Object> properties = new ConcurrentHashMap<String, Object>();

    private static final Logger LOG = LoggerFactory.getLogger(DefaultClientConfigImpl.class);

    private String clientName = null;

    private VipAddressResolver resolver = null;

    private boolean enableDynamicProperties = true;
    /**
     * Defaults for the parameters for the thread pool used by batchParallel
     * calls
     */
    public static final int DEFAULT_POOL_MAX_THREADS = DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS;
    public static final int DEFAULT_POOL_MIN_THREADS = 1;
    public static final long DEFAULT_POOL_KEEP_ALIVE_TIME = 15 * 60L;
    public static final TimeUnit DEFAULT_POOL_KEEP_ALIVE_TIME_UNITS = TimeUnit.SECONDS;
    public static final Boolean DEFAULT_ENABLE_ZONE_AFFINITY = Boolean.FALSE;
    public static final Boolean DEFAULT_ENABLE_ZONE_EXCLUSIVITY = Boolean.FALSE;
    public static final int DEFAULT_PORT = 7001;
    public static final Boolean DEFAULT_ENABLE_LOADBALANCER = Boolean.TRUE;

    public static final String DEFAULT_PROPERTY_NAME_SPACE = "ribbon";

    private String propertyNameSpace = DEFAULT_PROPERTY_NAME_SPACE;

    public static final Boolean DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS = Boolean.FALSE;

    public static final Boolean DEFAULT_ENABLE_NIWS_EVENT_LOGGING = Boolean.TRUE;

    public static final Boolean DEFAULT_IS_CLIENT_AUTH_REQUIRED = Boolean.FALSE;

    private final Map<String, DynamicStringProperty> dynamicProperties = new ConcurrentHashMap<String, DynamicStringProperty>();

    public Boolean getDefaultPrioritizeVipAddressBasedServers() {
		return DEFAULT_PRIORITIZE_VIP_ADDRESS_BASED_SERVERS;
	}

	public String getDefaultNfloadbalancerPingClassname() {
		return DEFAULT_NFLOADBALANCER_PING_CLASSNAME;
	}

	public String getDefaultNfloadbalancerRuleClassname() {
		return DEFAULT_NFLOADBALANCER_RULE_CLASSNAME;
	}

	public String getDefaultNfloadbalancerClassname() {
		return DEFAULT_NFLOADBALANCER_CLASSNAME;
	}

	public String getDefaultClientClassname() {
		return DEFAULT_CLIENT_CLASSNAME;
	}

	public String getDefaultVipaddressResolverClassname() {
		return DEFAULT_VIPADDRESS_RESOLVER_CLASSNAME;
	}

	public String getDefaultPrimeConnectionsUri() {
		return DEFAULT_PRIME_CONNECTIONS_URI;
	}

	public int getDefaultMaxTotalTimeToPrimeConnections() {
		return DEFAULT_MAX_TOTAL_TIME_TO_PRIME_CONNECTIONS;
	}

	public int getDefaultMaxRetriesPerServerPrimeConnection() {
		return DEFAULT_MAX_RETRIES_PER_SERVER_PRIME_CONNECTION;
	}

	public Boolean getDefaultEnablePrimeConnections() {
		return DEFAULT_ENABLE_PRIME_CONNECTIONS;
	}

	public int getDefaultMaxRequestsAllowedPerWindow() {
		return DEFAULT_MAX_REQUESTS_ALLOWED_PER_WINDOW;
	}

	public int getDefaultRequestThrottlingWindowInMillis() {
		return DEFAULT_REQUEST_THROTTLING_WINDOW_IN_MILLIS;
	}

	public Boolean getDefaultEnableRequestThrottling() {
		return DEFAULT_ENABLE_REQUEST_THROTTLING;
	}

	public Boolean getDefaultEnableGzipContentEncodingFilter() {
		return DEFAULT_ENABLE_GZIP_CONTENT_ENCODING_FILTER;
	}

	public Boolean getDefaultConnectionPoolCleanerTaskEnabled() {
		return DEFAULT_CONNECTION_POOL_CLEANER_TASK_ENABLED;
	}

	public Boolean getDefaultFollowRedirects() {
		return DEFAULT_FOLLOW_REDIRECTS;
	}

	public float getDefaultPercentageNiwsEventLogged() {
		return DEFAULT_PERCENTAGE_NIWS_EVENT_LOGGED;
	}

	public int getDefaultMaxAutoRetriesNextServer() {
		return DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER;
	}

	public int getDefaultMaxAutoRetries() {
		return DEFAULT_MAX_AUTO_RETRIES;
	}

	public int getDefaultReadTimeout() {
		return DEFAULT_READ_TIMEOUT;
	}

	public int getDefaultConnectionManagerTimeout() {
		return DEFAULT_CONNECTION_MANAGER_TIMEOUT;
	}

	public int getDefaultConnectTimeout() {
		return DEFAULT_CONNECT_TIMEOUT;
	}

	public int getDefaultMaxHttpConnectionsPerHost() {
		return DEFAULT_MAX_HTTP_CONNECTIONS_PER_HOST;
	}

	public int getDefaultMaxTotalHttpConnections() {
		return DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS;
	}

	public float getDefaultMinPrimeConnectionsRatio() {
		return DEFAULT_MIN_PRIME_CONNECTIONS_RATIO;
	}

	public String getDefaultPrimeConnectionsClass() {
		return DEFAULT_PRIME_CONNECTIONS_CLASS;
	}

	public String getDefaultSeverListClass() {
		return DEFAULT_SEVER_LIST_CLASS;
	}

	public int getDefaultConnectionIdleTimertaskRepeatInMsecs() {
		return DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS;
	}

	public int getDefaultConnectionidleTimeInMsecs() {
		return DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS;
	}

	public VipAddressResolver getResolver() {
		return resolver;
	}

	public boolean isEnableDynamicProperties() {
		return enableDynamicProperties;
	}

	public int getDefaultPoolMaxThreads() {
		return DEFAULT_POOL_MAX_THREADS;
	}

	public int getDefaultPoolMinThreads() {
		return DEFAULT_POOL_MIN_THREADS;
	}

	public long getDefaultPoolKeepAliveTime() {
		return DEFAULT_POOL_KEEP_ALIVE_TIME;
	}

	public TimeUnit getDefaultPoolKeepAliveTimeUnits() {
		return DEFAULT_POOL_KEEP_ALIVE_TIME_UNITS;
	}

	public Boolean getDefaultEnableZoneAffinity() {
		return DEFAULT_ENABLE_ZONE_AFFINITY;
	}

	public Boolean getDefaultEnableZoneExclusivity() {
		return DEFAULT_ENABLE_ZONE_EXCLUSIVITY;
	}

	public int getDefaultPort() {
		return DEFAULT_PORT;
	}

	public Boolean getDefaultEnableLoadbalancer() {
		return DEFAULT_ENABLE_LOADBALANCER;
	}


	public Boolean getDefaultOkToRetryOnAllOperations() {
		return DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS;
	}

	public Boolean getDefaultIsClientAuthRequired(){
		return DEFAULT_IS_CLIENT_AUTH_REQUIRED;
	}


	/**
	 * Create instance with no properties in default name space {@link #DEFAULT_PROPERTY_NAME_SPACE}
	 */
    public DefaultClientConfigImpl() {
        this.dynamicProperties.clear();
        this.enableDynamicProperties = false;
    }

	/**
	 * Create instance with no properties in the specified name space
	 */
    public DefaultClientConfigImpl(String nameSpace) {
    	this();
    	this.propertyNameSpace = nameSpace;
    }

    protected void loadDefaultValues() {
        putDefaultIntegerProperty(CommonClientConfigKey.MaxHttpConnectionsPerHost, getDefaultMaxHttpConnectionsPerHost());
        putDefaultIntegerProperty(CommonClientConfigKey.MaxTotalHttpConnections, getDefaultMaxTotalHttpConnections());
        putDefaultIntegerProperty(CommonClientConfigKey.ConnectTimeout, getDefaultConnectTimeout());
        putDefaultIntegerProperty(CommonClientConfigKey.ConnectionManagerTimeout, getDefaultConnectionManagerTimeout());
        putDefaultIntegerProperty(CommonClientConfigKey.ReadTimeout, getDefaultReadTimeout());
        putDefaultIntegerProperty(CommonClientConfigKey.MaxAutoRetries, getDefaultMaxAutoRetries());
        putDefaultIntegerProperty(CommonClientConfigKey.MaxAutoRetriesNextServer, getDefaultMaxAutoRetriesNextServer());
        putDefaultBooleanProperty(CommonClientConfigKey.OkToRetryOnAllOperations, getDefaultOkToRetryOnAllOperations());
        putDefaultBooleanProperty(CommonClientConfigKey.FollowRedirects, getDefaultFollowRedirects());
        putDefaultBooleanProperty(CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled, getDefaultConnectionPoolCleanerTaskEnabled());
        putDefaultIntegerProperty(CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds, getDefaultConnectionidleTimeInMsecs());
        putDefaultIntegerProperty(CommonClientConfigKey.ConnectionCleanerRepeatInterval, getDefaultConnectionIdleTimertaskRepeatInMsecs());
        putDefaultBooleanProperty(CommonClientConfigKey.EnableGZIPContentEncodingFilter, getDefaultEnableGzipContentEncodingFilter());
        String proxyHost = ConfigurationManager.getConfigInstance().getString(getDefaultPropName(CommonClientConfigKey.ProxyHost.key()));
        if (proxyHost != null && proxyHost.length() > 0) {
            setProperty(CommonClientConfigKey.ProxyHost, proxyHost);
        }
        Integer proxyPort = ConfigurationManager
                .getConfigInstance()
                .getInteger(
                        getDefaultPropName(CommonClientConfigKey.ProxyPort),
                        (Integer.MIN_VALUE + 1)); // + 1 just to avoid potential clash with user setting
        if (proxyPort != (Integer.MIN_VALUE + 1)) {
            setProperty(CommonClientConfigKey.ProxyPort, proxyPort);
        }
        putDefaultIntegerProperty(CommonClientConfigKey.Port, getDefaultPort());
        putDefaultBooleanProperty(CommonClientConfigKey.EnablePrimeConnections, getDefaultEnablePrimeConnections());
        putDefaultIntegerProperty(CommonClientConfigKey.MaxRetriesPerServerPrimeConnection, getDefaultMaxRetriesPerServerPrimeConnection());
        putDefaultIntegerProperty(CommonClientConfigKey.MaxTotalTimeToPrimeConnections, getDefaultMaxTotalTimeToPrimeConnections());
        putDefaultStringProperty(CommonClientConfigKey.PrimeConnectionsURI, getDefaultPrimeConnectionsUri());
        putDefaultIntegerProperty(CommonClientConfigKey.PoolMinThreads, getDefaultPoolMinThreads());
        putDefaultIntegerProperty(CommonClientConfigKey.PoolMaxThreads, getDefaultPoolMaxThreads());
        putDefaultLongProperty(CommonClientConfigKey.PoolKeepAliveTime, getDefaultPoolKeepAliveTime());
        putDefaultTimeUnitProperty(CommonClientConfigKey.PoolKeepAliveTimeUnits, getDefaultPoolKeepAliveTimeUnits());
        putDefaultBooleanProperty(CommonClientConfigKey.EnableZoneAffinity, getDefaultEnableZoneAffinity());
        putDefaultBooleanProperty(CommonClientConfigKey.EnableZoneExclusivity, getDefaultEnableZoneExclusivity());
        putDefaultStringProperty(CommonClientConfigKey.ClientClassName, getDefaultClientClassname());
        putDefaultStringProperty(CommonClientConfigKey.NFLoadBalancerClassName, getDefaultNfloadbalancerClassname());
        putDefaultStringProperty(CommonClientConfigKey.NFLoadBalancerRuleClassName, getDefaultNfloadbalancerRuleClassname());
        putDefaultStringProperty(CommonClientConfigKey.NFLoadBalancerPingClassName, getDefaultNfloadbalancerPingClassname());
        putDefaultBooleanProperty(CommonClientConfigKey.PrioritizeVipAddressBasedServers, getDefaultPrioritizeVipAddressBasedServers());
        putDefaultFloatProperty(CommonClientConfigKey.MinPrimeConnectionsRatio, getDefaultMinPrimeConnectionsRatio());
        putDefaultStringProperty(CommonClientConfigKey.PrimeConnectionsClassName, getDefaultPrimeConnectionsClass());
        putDefaultStringProperty(CommonClientConfigKey.NIWSServerListClassName, getDefaultSeverListClass());
        putDefaultStringProperty(CommonClientConfigKey.VipAddressResolverClassName, getDefaultVipaddressResolverClassname());
        putDefaultBooleanProperty(CommonClientConfigKey.IsClientAuthRequired, getDefaultIsClientAuthRequired());
    }

    protected void setPropertyInternal(IClientConfigKey propName, Object value) {
        setPropertyInternal(propName.key(), value);
    }

    private String getConfigKey(String propName) {
        return (clientName == null) ? getDefaultPropName(propName) : getInstancePropName(clientName, propName);
    }

    protected void setPropertyInternal(final String propName, Object value) {
        String stringValue = (value == null) ? "" : String.valueOf(value);
        properties.put(propName, stringValue);
        if (!enableDynamicProperties) {
            return;
        }
        String configKey = getConfigKey(propName);
        final DynamicStringProperty prop = DynamicPropertyFactory.getInstance().getStringProperty(configKey, null);
        Runnable callback = new Runnable() {
            @Override
            public void run() {
                String value = prop.get();
                if (value != null) {
                    properties.put(propName, value);
                } else {
                    properties.remove(propName);
                }
            }

            // equals and hashcode needed
            // since this is anonymous object is later used as a set key

            @Override
            public boolean equals(Object other){
            	if (other == null) {
            		return false;
            	}
            	if (getClass() == other.getClass()) {
                    return toString().equals(other.toString());
                }
                return false;
            }

            @Override
            public String toString(){
            	return propName;
            }

            @Override
            public int hashCode(){
            	return propName.hashCode();
            }


        };
        prop.addCallback(callback);
        dynamicProperties.put(propName, prop);
    }


	// Helper methods which first check if a "default" (with rest client name)
	// property exists. If so, that value is used, else the default value
	// passed as argument is used to put into the properties member variable
    protected void putDefaultIntegerProperty(IClientConfigKey propName, Integer defaultValue) {
        Integer value = ConfigurationManager.getConfigInstance().getInteger(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }

    protected void putDefaultLongProperty(IClientConfigKey propName, Long defaultValue) {
        Long value = ConfigurationManager.getConfigInstance().getLong(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }

    protected void putDefaultFloatProperty(IClientConfigKey propName, Float defaultValue) {
        Float value = ConfigurationManager.getConfigInstance().getFloat(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }

    protected void putDefaultTimeUnitProperty(IClientConfigKey propName, TimeUnit defaultValue) {
        TimeUnit value = defaultValue;
        String propValue = ConfigurationManager.getConfigInstance().getString(
                getDefaultPropName(propName));
        if(propValue != null && propValue.length() > 0) {
            value = TimeUnit.valueOf(propValue);
        }
        setPropertyInternal(propName, value);
    }

    String getDefaultPropName(String propName) {
        return getNameSpace() + "." + propName;
    }

    public String getDefaultPropName(IClientConfigKey propName) {
        return getDefaultPropName(propName.key());
    }


    protected void putDefaultStringProperty(IClientConfigKey propName, String defaultValue) {
        String value = ConfigurationManager.getConfigInstance().getString(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }

    protected void putDefaultBooleanProperty(IClientConfigKey propName, Boolean defaultValue) {
        Boolean value = ConfigurationManager.getConfigInstance().getBoolean(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }

    public void setClientName(String clientName){
        this.clientName  = clientName;
    }

    /* (non-Javadoc)
	 * @see com.netflix.niws.client.CliengConfig#getClientName()
	 */
    @Override
	public String getClientName() {
        return clientName;
    }

    /**
     * Load properties for a given client. It first loads the default values for all properties,
     * and any properties already defined with Archaius ConfigurationManager.
     */
    @Override
	public void loadProperties(String restClientName){
        enableDynamicProperties = true;
        setClientName(restClientName);
        loadDefaultValues();
        Configuration props = ConfigurationManager.getConfigInstance().subset(restClientName);
        for (Iterator<String> keys = props.getKeys(); keys.hasNext(); ){
            String key = keys.next();
            String prop = key;
            if (prop.startsWith(getNameSpace())){
                prop = prop.substring(getNameSpace().length() + 1);
            }
            setPropertyInternal(prop, props.getProperty(key));
        }

    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "DC_DOUBLECHECK")
    private VipAddressResolver getVipAddressResolver() {
        if (resolver == null) {
            synchronized (this) {
                if (resolver == null) {
                    try {
                        resolver = (VipAddressResolver) Class.forName(
                                (String) getProperty(CommonClientConfigKey.VipAddressResolverClassName)).newInstance();
                    } catch (Throwable e) {
                        LOG.error("Cannot instantiate VipAddressResolver", e);
                    }
                }
            }
        }
        return resolver;

    }

    @Override
	public String resolveDeploymentContextbasedVipAddresses(){

        String deploymentContextBasedVipAddressesMacro = (String) getProperty(CommonClientConfigKey.DeploymentContextBasedVipAddresses);
        return getVipAddressResolver().resolve(deploymentContextBasedVipAddressesMacro, this);
    }

    public String getAppName(){
        String appName = null;
        Object an = getProperty(CommonClientConfigKey.AppName);
        if (an!=null){
            appName = "" + an;
        }
        return appName;
    }

    public String getVersion(){
        String version = null;
        Object an = getProperty(CommonClientConfigKey.Version);
        if (an!=null){
            version = "" + an;
        }
        return version;
    }

    /* (non-Javadoc)
	 * @see com.netflix.niws.client.CliengConfig#getProperties()
	 */
    @Override
	public  Map<String, Object> getProperties() {
        return properties;
    }

    /* (non-Javadoc)
	 * @see com.netflix.niws.client.CliengConfig#setProperty(com.netflix.niws.client.ClientConfigKey, java.lang.Object)
	 */
    @Override
	public void setProperty(IClientConfigKey key, Object value){
        setPropertyInternal(key.key(), value);
    }

    public IClientConfig applyOverride(IClientConfig override) {
        if (override == null) {
            return this;
        }
        Map<String, Object> props = override.getProperties();
        for (Map.Entry<String, Object> entry: props.entrySet()) {
        	String key = entry.getKey();
        	Object value = entry.getValue();
        	if (key != null && value != null) {
        	    setPropertyInternal(key, value);
        	}
        }
        return this;
    }

    protected Object getProperty(String key) {
        DynamicStringProperty dynamicProperty = dynamicProperties.get(key);
        if (dynamicProperty != null) {
            String dynamicValue = dynamicProperty.get();
            if (dynamicValue != null) {
                return dynamicValue;
            }
        }
        return properties.get(key);
    }

    /* (non-Javadoc)
	 * @see com.netflix.niws.client.CliengConfig#getProperty(com.netflix.niws.client.ClientConfigKey)
	 */
    @Override
	public Object getProperty(IClientConfigKey key){
        String propName = key.key();
        return getProperty(propName);
    }

    /* (non-Javadoc)
	 * @see com.netflix.niws.client.CliengConfig#getProperty(com.netflix.niws.client.ClientConfigKey, java.lang.Object)
	 */
    @Override
	public Object getProperty(IClientConfigKey key, Object defaultVal){
        Object val = getProperty(key);
        if (val == null){
            return defaultVal;
        }
        return val;
    }

    public static Object getProperty(Map<String, Object> config, IClientConfigKey key, Object defaultVal) {
        Object val = config.get(key.key());
        if (val == null) {
            return defaultVal;
        }
        return val;
    }

    public static Object getProperty(Map<String, Object> config, IClientConfigKey key) {
        return getProperty(config, key, null);
    }

    public boolean isSecure() {
        Object oo = getProperty(CommonClientConfigKey.IsSecure);
        if (oo != null) {
            return Boolean.parseBoolean(oo.toString());
        } else {
        	return false;
        }
    }

    /* (non-Javadoc)
	 * @see com.netflix.niws.client.CliengConfig#containsProperty(com.netflix.niws.client.ClientConfigKey)
	 */
    @Override
	public boolean containsProperty(IClientConfigKey key){
        Object oo = getProperty(key);
        return oo!=null? true: false;
    }

    @Override
    public String toString(){
        final StringBuilder sb = new StringBuilder();
        String separator = "";

        sb.append("NiwsClientConfig:");
        for (IClientConfigKey key: CommonClientConfigKey.values()) {
            final Object value = getProperty(key);

            sb.append(separator);
            separator = ", ";
            sb.append(key).append(":");
            if (key.key().endsWith("Password") && value instanceof String) {
                sb.append(Strings.repeat("*", ((String) value).length()));
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
    }

    public void setProperty(Properties props, String restClientName, String key, String value){
        props.setProperty( getInstancePropName(restClientName, key), value);
    }

    public String getInstancePropName(String restClientName,
            IClientConfigKey configKey) {
        return getInstancePropName(restClientName, configKey.key());
    }

    public String getInstancePropName(String restClientName, String key) {
        return restClientName + "." + getNameSpace() + "."
                + key;
    }


	@Override
	public String getNameSpace() {
		return propertyNameSpace;
	}

	public static DefaultClientConfigImpl getClientConfigWithDefaultValues(String clientName) {
		return getClientConfigWithDefaultValues(clientName, DEFAULT_PROPERTY_NAME_SPACE);
	}

	public static DefaultClientConfigImpl getClientConfigWithDefaultValues(String clientName, String nameSpace) {
		DefaultClientConfigImpl config = new DefaultClientConfigImpl(nameSpace);
		config.loadProperties(clientName);
		return config;
	}
}
