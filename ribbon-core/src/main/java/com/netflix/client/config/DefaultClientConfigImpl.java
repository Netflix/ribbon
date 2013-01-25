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

import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.netflix.client.VipAddressResolver;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.config.util.HttpVerbUriRegexPropertyValue;
import com.netflix.loadbalancer.DummyPing;

/**
 * Class that holds the NIWS Client Configuration to be used for <code>{@link RestClient}</code>)
 * @author Sudhir Tonse <stonse@netflix.com>
 *
 */
public class DefaultClientConfigImpl implements IClientConfig {

    public static final Boolean DEFAULT_PRIORITIZE_VIP_ADDRESS_BASED_SERVERS = Boolean.TRUE;

	public static final String DEFAULT_NFLOADBALANCER_PING_CLASSNAME = DummyPing.class.getName();

    public static final String DEFAULT_NFLOADBALANCER_RULE_CLASSNAME = com.netflix.loadbalancer.AvailabilityFilteringRule.class.getName();

    public static final String DEFAULT_NFLOADBALANCER_CLASSNAME = com.netflix.loadbalancer.ZoneAwareLoadBalancer.class.getName();
    
    public static final String DEFAULT_CLIENT_CLASSNAME = "com.netflix.niws.client.http.RestClient";
    
    public static final String DEFAULT_VIPADDRESS_RESOLVER_CLASSNAME = com.netflix.client.SimpleVipAddressResolver.class.getName();

    public static final String DEFAULT_PRIME_CONNECTIONS_URI = "/";

    public static final int DEFAULT_MAX_TOTAL_TIME_TO_PRIME_CONNECTIONS = 30*1000;

    public static final int DEFAULT_MAX_RETRIES_PER_SERVER_PRIME_CONNECTION = 2;

    public static final Boolean DEFAULT_ENABLE_PRIME_CONNECTIONS = Boolean.FALSE;

    public static final int DEFAULT_MAX_REQUESTS_ALLOWED_PER_WINDOW = Integer.MAX_VALUE;

    public static final int DEFAULT_REQUEST_THROTTLING_WINDOW_IN_MILLIS = 60*1000;

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
    
    public static final Boolean DEFAULT_ENABLE_NIWSSTATS = Boolean.TRUE;
    
    public static final Boolean DEFAULT_ENABLE_NIWSERRORSTATS = Boolean.TRUE;
    
    public static final Boolean DEFAULT_USE_HTTP_CLIENT4 = Boolean.FALSE;
    
    public static final float DEFAULT_MIN_PRIME_CONNECTIONS_RATIO = 1.0f;
    
    public static final String DEFAULT_PRIME_CONNECTIONS_CLASS = "com.netflix.niws.client.HttpPrimeConnection";
    
    public static final String DEFAULT_SEVER_LIST_CLASS = com.netflix.loadbalancer.ConfigurationBasedServerList.class.getName();
    
    public static final int DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS = 30*1000; // every half minute (30 secs)
    
    public static final int DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS = 30*1000; // all connections idle for 30 secs

    volatile Map<String, Object> properties = new ConcurrentHashMap<String, Object>();
    
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
        
    private Map<String, DynamicStringProperty> dynamicProperties = new ConcurrentHashMap<String, DynamicStringProperty>();

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

	public Boolean getDefaultEnableNiwsstats() {
		return DEFAULT_ENABLE_NIWSSTATS;
	}

	public Boolean getDefaultEnableNiwserrorstats() {
		return DEFAULT_ENABLE_NIWSERRORSTATS;
	}

	public Boolean getDefaultUseHttpClient4() {
		return DEFAULT_USE_HTTP_CLIENT4;
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

            
    public DefaultClientConfigImpl() {
        this.dynamicProperties.clear();
        this.enableDynamicProperties = false;
    }

    public DefaultClientConfigImpl(String nameSpace) {
    	this();
    	this.propertyNameSpace = nameSpace;
    }
        
    public void loadDefaultProperties() {
        putIntegerProperty(CommonClientConfigKey.MaxHttpConnectionsPerHost, getDefaultMaxHttpConnectionsPerHost());
        putIntegerProperty(CommonClientConfigKey.MaxTotalHttpConnections, getDefaultMaxTotalHttpConnections());
        putIntegerProperty(CommonClientConfigKey.ConnectTimeout, getDefaultConnectTimeout());
        putIntegerProperty(CommonClientConfigKey.ConnectionManagerTimeout, getDefaultConnectionManagerTimeout());
        putIntegerProperty(CommonClientConfigKey.ReadTimeout, getDefaultReadTimeout());
        putIntegerProperty(CommonClientConfigKey.MaxAutoRetries, getDefaultMaxAutoRetries());
        putIntegerProperty(CommonClientConfigKey.MaxAutoRetriesNextServer, getDefaultMaxAutoRetriesNextServer());
        putBooleanProperty(CommonClientConfigKey.OkToRetryOnAllOperations, getDefaultOkToRetryOnAllOperations());
        putBooleanProperty(CommonClientConfigKey.FollowRedirects, getDefaultFollowRedirects());
        putBooleanProperty(CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled, getDefaultConnectionPoolCleanerTaskEnabled()); 
        putIntegerProperty(CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds, getDefaultConnectionidleTimeInMsecs());
        putIntegerProperty(CommonClientConfigKey.ConnectionCleanerRepeatInterval, getDefaultConnectionIdleTimertaskRepeatInMsecs());
        putBooleanProperty(CommonClientConfigKey.EnableGZIPContentEncodingFilter, getDefaultEnableGzipContentEncodingFilter());
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
        putIntegerProperty(CommonClientConfigKey.Port, getDefaultPort());
        putBooleanProperty(CommonClientConfigKey.EnablePrimeConnections, getDefaultEnablePrimeConnections());
        putIntegerProperty(CommonClientConfigKey.MaxRetriesPerServerPrimeConnection, getDefaultMaxRetriesPerServerPrimeConnection());
        putIntegerProperty(CommonClientConfigKey.MaxTotalTimeToPrimeConnections, getDefaultMaxTotalTimeToPrimeConnections());
        putStringProperty(CommonClientConfigKey.PrimeConnectionsURI, getDefaultPrimeConnectionsUri());
        putIntegerProperty(CommonClientConfigKey.PoolMinThreads, getDefaultPoolMinThreads());
        putIntegerProperty(CommonClientConfigKey.PoolMaxThreads, getDefaultPoolMaxThreads());
        putLongProperty(CommonClientConfigKey.PoolKeepAliveTime, getDefaultPoolKeepAliveTime());
        putTimeUnitProperty(CommonClientConfigKey.PoolKeepAliveTimeUnits, getDefaultPoolKeepAliveTimeUnits());
        putBooleanProperty(CommonClientConfigKey.EnableZoneAffinity, getDefaultEnableZoneAffinity());
        putBooleanProperty(CommonClientConfigKey.EnableZoneExclusivity, getDefaultEnableZoneExclusivity());
        putStringProperty(CommonClientConfigKey.ClientClassName, getDefaultClientClassname());
        putStringProperty(CommonClientConfigKey.NFLoadBalancerClassName, getDefaultNfloadbalancerClassname());
        putStringProperty(CommonClientConfigKey.NFLoadBalancerRuleClassName, getDefaultNfloadbalancerRuleClassname());
        putStringProperty(CommonClientConfigKey.NFLoadBalancerPingClassName, getDefaultNfloadbalancerPingClassname());
        putBooleanProperty(CommonClientConfigKey.PrioritizeVipAddressBasedServers, getDefaultPrioritizeVipAddressBasedServers());
        putFloatProperty(CommonClientConfigKey.MinPrimeConnectionsRatio, getDefaultMinPrimeConnectionsRatio());
        putStringProperty(CommonClientConfigKey.PrimeConnectionsClassName, getDefaultPrimeConnectionsClass());
        putStringProperty(CommonClientConfigKey.NIWSServerListClassName, getDefaultSeverListClass());
        putStringProperty(CommonClientConfigKey.VipAddressResolverClassName, getDefaultVipaddressResolverClassname());    	
    }
    
    private void setPropertyInternal(IClientConfigKey propName, Object value) {
        setPropertyInternal(propName.key(), value);
    }

    private String getConfigKey(String propName) {
        return (clientName == null) ? getDefaultPropName(propName) : getInstancePropName(clientName, propName);
    }
    
    private void setPropertyInternal(final String propName, Object value) {
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
    private void putIntegerProperty(IClientConfigKey propName, Integer defaultValue) {
        Integer value = ConfigurationManager.getConfigInstance().getInteger(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }

    private void putLongProperty(IClientConfigKey propName, Long defaultValue) {
        Long value = ConfigurationManager.getConfigInstance().getLong(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }
    
    private void putFloatProperty(IClientConfigKey propName, Float defaultValue) {
        Float value = ConfigurationManager.getConfigInstance().getFloat(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }
    
    private void putTimeUnitProperty(IClientConfigKey propName, TimeUnit defaultValue) {
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

    
    private void putStringProperty(IClientConfigKey propName, String defaultValue) {
        String value = ConfigurationManager.getConfigInstance().getString(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }
    
    private void putBooleanProperty(IClientConfigKey propName, Boolean defaultValue) {
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

    /* (non-Javadoc)
	 * @see com.netflix.niws.client.CliengConfig#loadProperties(java.lang.String)
	 */
    @Override
	public void loadProperties(String restClientName){
        enableDynamicProperties = true;
        setClientName(restClientName);
        loadDefaultProperties();
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
        for (IClientConfigKey key: CommonClientConfigKey.values()) {
            Object value = override.getProperty(key);
            if (value != null) {
                setProperty(key, value);
            }
        }
        return this;
    }
    
    /* (non-Javadoc)
	 * @see com.netflix.niws.client.CliengConfig#getProperty(com.netflix.niws.client.ClientConfigKey)
	 */
    @Override
	public Object getProperty(IClientConfigKey key){
        String propName = key.key();
        DynamicStringProperty dynamicProperty = dynamicProperties.get(propName);
        if (dynamicProperty != null) {
            String dynamicValue = dynamicProperty.get();
            if (dynamicValue != null) {
                return dynamicValue;
            }
        }
        return properties.get(propName);
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

    
    private void initializeDynamicConfig(String prefix, 
            ConcurrentHashMap<String, Map<String, HttpVerbUriRegexPropertyValue>> configMapForPrefix) {
        AbstractConfiguration configInstance = ConfigurationManager.getConfigInstance();
        // load any pre-configured dynamic properties
        String niwsPropertyPrefix = getClientName() + "."
        + getNameSpace();
        String prefixWithNoDot = niwsPropertyPrefix + "." + prefix;
        String configPrefix = prefixWithNoDot + ".";

        if (configInstance != null) {
            Configuration c = configInstance.subset(prefixWithNoDot);
            if (c != null) {
                Iterator<?> it = c.getKeys();
                if (it != null) {
                    while (it.hasNext()) {
                        String alias = (String) it.next();
                        if (alias != null) {
                            // we have a property of interest - add it to
                            // our
                            // map
                            String value = configInstance.getString(configPrefix + alias);
                            if (value != null) {
                                Map<String, HttpVerbUriRegexPropertyValue> aliasMap = configMapForPrefix.get(getClientName());
                                if (aliasMap == null) {
                                    aliasMap = new ConcurrentHashMap<String, HttpVerbUriRegexPropertyValue>();
                                    Map<String, HttpVerbUriRegexPropertyValue> prev = configMapForPrefix.putIfAbsent(getClientName(),
                                            aliasMap);
                                    if (prev != null) {
                                    	aliasMap = prev;
                                    }
                                }
                                aliasMap.put(alias.trim(),
                                        HttpVerbUriRegexPropertyValue
                                                .getVerbUriRegex(value
                                                        .toString()));

                            }
                        }
                    }
                }
            }
        }

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
		// TODO Auto-generated method stub
		return propertyNameSpace;
	}	
}
