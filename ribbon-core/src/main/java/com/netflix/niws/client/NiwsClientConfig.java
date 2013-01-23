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
package com.netflix.niws.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.netflix.config.AbstractDynamicPropertyListener;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.config.ExpandedConfigurationListenerAdapter;
import com.netflix.config.util.HttpVerbUriRegexPropertyValue;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.niws.VipAddressResolver;

/**
 * Class that holds the NIWS Client Configuration to be used for <code>{@link RestClient}</code>)
 * @author Sudhir Tonse <stonse@netflix.com>
 *
 */
public class NiwsClientConfig implements IClientConfig {

    public static final Boolean DEFAULT_PRIORITIZE_VIP_ADDRESS_BASED_SERVERS = Boolean.TRUE;

    public static final String DEFAULT_NFLOADBALANCER_PING_CLASSNAME = DummyPing.class.getName();

    public static final String DEFAULT_NFLOADBALANCER_RULE_CLASSNAME = com.netflix.niws.client.AvailabilityFilteringRule.class.getName();

    public static final String DEFAULT_NFLOADBALANCER_CLASSNAME = com.netflix.niws.client.ZoneAwareNIWSDiscoveryLoadBalancer.class.getName();
    
    public static final String DEFAULT_CLIENT_CLASSNAME = "com.netflix.niws.client.http.RestClient";
    
    public static final String DEFAULT_VIPADDRESS_RESOLVER_CLASSNAME = com.netflix.niws.SimpleVipAddressResolver.class.getName();

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
    
    public static final String DEFAULT_SEVER_LIST_CLASS = "com.netflix.niws.client.DiscoveryEnabledNIWSServerList";
    
    public static final int DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS = 30*1000; // every half minute (30 secs)
    
    public static final int DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS = 30*1000; // all connections idle for 30 secs


    
    volatile Map<String, Object> properties = new ConcurrentHashMap<String, Object>();
    
    private static final Logger LOG = LoggerFactory.getLogger(NiwsClientConfig.class);

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

    private static final String PROPERTY_NAMESPACE = "niws.client";

    public static final Boolean DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS = Boolean.FALSE;
    
    private static ConcurrentHashMap<String, NiwsClientConfig> namedConfig = new ConcurrentHashMap<String, NiwsClientConfig>();

    public static final Boolean DEFAULT_ENABLE_NIWS_EVENT_LOGGING = Boolean.TRUE;

    private static ConcurrentHashMap<String, ConcurrentHashMap<String, Map<String, HttpVerbUriRegexPropertyValue>>> dynamicConfigMap =
        new ConcurrentHashMap<String, ConcurrentHashMap<String, Map<String, HttpVerbUriRegexPropertyValue>>>();
    
    private static final String[] DYNAMIC_PROPERTY_PREFIX = {"SLA", "NIWSStats", "ResponseCache", "MethodURI"};
    
    private Map<String, DynamicStringProperty> dynamicProperties = new ConcurrentHashMap<String, DynamicStringProperty>();
        
    static{
        ConfigurationManager.getConfigInstance().addConfigurationListener(
                new ExpandedConfigurationListenerAdapter(new NiwsConfigListener()));
    }    
    
    public NiwsClientConfig() {
        this.dynamicProperties.clear();
        this.enableDynamicProperties = false;
    }

    public NiwsClientConfig(Map<String, Object> properties) {
        if (properties != null) {
            for (IClientConfigKey niwsKey: CommonClientConfigKey.values()) {
                String key = niwsKey.key();
                Object value = properties.get(key);
                if (value != null) {
                    this.properties.put(key, value);
                }
            }
        }
        this.dynamicProperties.clear();
        this.enableDynamicProperties = false;
    }
    
    public static NiwsClientConfig getConfigWithDefaultProperties() {
        NiwsClientConfig config = new NiwsClientConfig();
        config.enableDynamicProperties = true;
        //Defaults
        config.putBooleanProperty(CommonClientConfigKey.UseHttpClient4, DEFAULT_USE_HTTP_CLIENT4);
        config.putIntegerProperty(CommonClientConfigKey.MaxHttpConnectionsPerHost, Integer.valueOf(DEFAULT_MAX_HTTP_CONNECTIONS_PER_HOST));
        config.putIntegerProperty(CommonClientConfigKey.MaxTotalHttpConnections, Integer.valueOf(DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS));
        config.putIntegerProperty(CommonClientConfigKey.ConnectTimeout, Integer.valueOf(DEFAULT_CONNECT_TIMEOUT));
        config.putIntegerProperty(CommonClientConfigKey.ConnectionManagerTimeout, Integer.valueOf(DEFAULT_CONNECTION_MANAGER_TIMEOUT));
        config.putIntegerProperty(CommonClientConfigKey.ReadTimeout, Integer.valueOf(DEFAULT_READ_TIMEOUT));
        config.putIntegerProperty(CommonClientConfigKey.MaxAutoRetries, Integer.valueOf(DEFAULT_MAX_AUTO_RETRIES));
        config.putIntegerProperty(CommonClientConfigKey.MaxAutoRetriesNextServer, Integer.valueOf(DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER));
        config.putBooleanProperty(CommonClientConfigKey.OkToRetryOnAllOperations, DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS);
        config.putBooleanProperty(CommonClientConfigKey.EnableNIWSEventLogging, DEFAULT_ENABLE_NIWS_EVENT_LOGGING);
        config.putFloatProperty(CommonClientConfigKey.PercentageNIWSEventLogged, Float.valueOf(DEFAULT_PERCENTAGE_NIWS_EVENT_LOGGED));  // 0=only log what the calling context suggests
        config.putBooleanProperty(CommonClientConfigKey.FollowRedirects, DEFAULT_FOLLOW_REDIRECTS);
        config.putBooleanProperty(CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled, DEFAULT_CONNECTION_POOL_CLEANER_TASK_ENABLED); // default is true for RestClient
        config.putIntegerProperty(CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds,
            Integer.valueOf(DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS));
        config.putIntegerProperty(CommonClientConfigKey.ConnectionCleanerRepeatInterval,
            Integer.valueOf(DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS));
        config.putBooleanProperty(CommonClientConfigKey.EnableGZIPContentEncodingFilter, DEFAULT_ENABLE_GZIP_CONTENT_ENCODING_FILTER);
        config.putBooleanProperty(CommonClientConfigKey.EnableRequestThrottling, DEFAULT_ENABLE_REQUEST_THROTTLING);
        config.putIntegerProperty(CommonClientConfigKey.RequestThrottlingWindowInMSecs, Integer.valueOf(DEFAULT_REQUEST_THROTTLING_WINDOW_IN_MILLIS));
        config.putIntegerProperty(CommonClientConfigKey.MaxRequestsAllowedPerWindow, Integer.valueOf(DEFAULT_MAX_REQUESTS_ALLOWED_PER_WINDOW));
        String proxyHost = ConfigurationManager.getConfigInstance().getString(getDefaultPropName(CommonClientConfigKey.ProxyHost.key()));
        if (proxyHost != null && proxyHost.length() > 0) {
            config.setProperty(CommonClientConfigKey.ProxyHost, proxyHost);
        }
        Integer proxyPort = ConfigurationManager
                .getConfigInstance()
                .getInteger(
                        getDefaultPropName(CommonClientConfigKey.ProxyPort),
                        (Integer.MIN_VALUE + 1)); // + 1 just to avoid potential clash with user setting
        if (proxyPort != (Integer.MIN_VALUE + 1)) {
            config.setProperty(CommonClientConfigKey.ProxyPort, proxyPort);
        }
        config.putIntegerProperty(CommonClientConfigKey.Port, Integer.valueOf(DEFAULT_PORT));
        config.putBooleanProperty(CommonClientConfigKey.EnablePrimeConnections, DEFAULT_ENABLE_PRIME_CONNECTIONS);
        config.putIntegerProperty(CommonClientConfigKey.MaxRetriesPerServerPrimeConnection, Integer.valueOf(DEFAULT_MAX_RETRIES_PER_SERVER_PRIME_CONNECTION));
        config.putIntegerProperty(CommonClientConfigKey.MaxTotalTimeToPrimeConnections, Integer.valueOf(DEFAULT_MAX_TOTAL_TIME_TO_PRIME_CONNECTIONS));
        config.putStringProperty(CommonClientConfigKey.PrimeConnectionsURI, DEFAULT_PRIME_CONNECTIONS_URI);
        config.putIntegerProperty(CommonClientConfigKey.PoolMinThreads, Integer.valueOf(DEFAULT_POOL_MIN_THREADS));
        config.putIntegerProperty(CommonClientConfigKey.PoolMaxThreads, Integer.valueOf(DEFAULT_POOL_MAX_THREADS));
        config.putLongProperty(CommonClientConfigKey.PoolKeepAliveTime, Long.valueOf(DEFAULT_POOL_KEEP_ALIVE_TIME));
        config.putTimeUnitProperty(CommonClientConfigKey.PoolKeepAliveTimeUnits,DEFAULT_POOL_KEEP_ALIVE_TIME_UNITS);
        config.putBooleanProperty(CommonClientConfigKey.EnableZoneAffinity, DEFAULT_ENABLE_ZONE_AFFINITY);
        config.putBooleanProperty(CommonClientConfigKey.EnableZoneExclusivity, DEFAULT_ENABLE_ZONE_EXCLUSIVITY);
        config.putStringProperty(CommonClientConfigKey.ClientClassName, DEFAULT_CLIENT_CLASSNAME);
        config.putStringProperty(CommonClientConfigKey.NFLoadBalancerClassName, DEFAULT_NFLOADBALANCER_CLASSNAME);
        config.putStringProperty(CommonClientConfigKey.NFLoadBalancerRuleClassName, DEFAULT_NFLOADBALANCER_RULE_CLASSNAME);
        config.putStringProperty(CommonClientConfigKey.NFLoadBalancerPingClassName, DEFAULT_NFLOADBALANCER_PING_CLASSNAME);
        config.putBooleanProperty(CommonClientConfigKey.PrioritizeVipAddressBasedServers, DEFAULT_PRIORITIZE_VIP_ADDRESS_BASED_SERVERS);
        config.putBooleanProperty(CommonClientConfigKey.EnableNIWSStats, DEFAULT_ENABLE_NIWSSTATS);
        config.putBooleanProperty(CommonClientConfigKey.EnableNIWSErrorStats, DEFAULT_ENABLE_NIWSERRORSTATS);
        config.putFloatProperty(CommonClientConfigKey.MinPrimeConnectionsRatio, DEFAULT_MIN_PRIME_CONNECTIONS_RATIO);
        config.putBooleanProperty(CommonClientConfigKey.UseTunnel, Boolean.FALSE);
        config.putStringProperty(CommonClientConfigKey.PrimeConnectionsClassName, DEFAULT_PRIME_CONNECTIONS_CLASS);
        // putBooleanProperty(CommonClientConfigKey.PrioritizeIntStack, Boolean.FALSE);
        config.putStringProperty(CommonClientConfigKey.VipAddressResolverClassName, DEFAULT_VIPADDRESS_RESOLVER_CLASSNAME);
        return config;
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
    
    static String getDefaultPropName(String propName) {
        return PROPERTY_NAMESPACE + "." + propName;
    }

    public static String getDefaultPropName(IClientConfigKey propName) {
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
    
    /**
     * Enum Class to contain properties of the Client
     * @author stonse
     *
     */
    public enum NiwsClientConfigKey implements IClientConfigKey {

        AppName(CommonClientConfigKey.AppName.key()),
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
        EnableNIWSEventLogging("EnableNIWSEventLogging"),
        EnnableVerboseErrorLogging("EnableVerboseErrorLogging"),
        PercentageNIWSEventLogged("PercentageNIWSEventLogged"),
        EnableRequestThrottling("EnableRequestThrottling"),
        RequestThrottlingWindowInMSecs("RequestThrottlingWindowInMSecs"),
        MaxRequestsAllowedPerWindow("MaxRequestsAllowedPerWindow"),        
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
        SLA("SLA"),
        SLAMinFailureResponseCode("SLAMinFailureResponseCode"),
        EnableNIWSStats("EnableNIWSStats"), // enable the feature of collecting request stats
        EnableNIWSErrorStats("EnableNIWSErrorStats"), // capture numErrors and other stats per Error Code
        NIWSStats("NIWSStats"), // The property key used per request stat alias

        //HTTP Client Related
        UseHttpClient4("UseHttpClient4"),
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
        VipAddressResolverClassName("VipAddressResolverClassName"),

        //Tunnelling
        UseTunnel("UseTunnel");

        private final String configKey;

        NiwsClientConfigKey(String configKey) {
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
        setClientName(restClientName);
        Configuration props = ConfigurationManager.getConfigInstance().subset(restClientName);        
        for (Iterator<String> keys = props.getKeys(); keys.hasNext(); ){
            String key = keys.next();
            String prop = key;
            if (prop.startsWith(PROPERTY_NAMESPACE)){
                prop = prop.substring(PROPERTY_NAMESPACE.length() + 1);
            }
            setPropertyInternal(prop, props.getProperty(key));
        }
        
        for (String dynamicPropPrefix: DYNAMIC_PROPERTY_PREFIX) {
            ConcurrentHashMap<String, Map<String, HttpVerbUriRegexPropertyValue>> map = new ConcurrentHashMap<String, Map<String, HttpVerbUriRegexPropertyValue>>();
            ConcurrentHashMap<String, Map<String, HttpVerbUriRegexPropertyValue>> previous = dynamicConfigMap.putIfAbsent(dynamicPropPrefix, map);
            if(previous != null) {
                map = previous;
            }
            initializeDynamicConfig(dynamicPropPrefix, map);
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

    /**
     * Set the underlying properties cache. This may cause inconsistencies with dynamic properties.
     * Instead, use {@link #setProperty(NiwsClientConfigKey, Object)} to set property.
     * 
     * @param properties
     */
    @Deprecated 
    public void setProperties(Map properties) {
        this.properties = (Map<String, Object>) properties;
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
    
    /**
     * Set a property. Should use {@link #setProperty(NiwsClientConfigKey, Object)} instead.    
     * 
     * @param key
     * @param value
     */
    @Deprecated
    public void setProperty(String key, Object value){
        setPropertyInternal(key, value);
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
        + PROPERTY_NAMESPACE;
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
    

    Map<String, HttpVerbUriRegexPropertyValue> getSlaAliasRuleMap() {
        return getDynamicPropMap("SLA");
    }

    private Map<String, HttpVerbUriRegexPropertyValue> getDynamicPropMap(String dynamicPropPrefix) {
        Map<String,HttpVerbUriRegexPropertyValue> map = new HashMap<String,HttpVerbUriRegexPropertyValue>();
        try{
            map = dynamicConfigMap.get(dynamicPropPrefix).get(getClientName());
        }catch(Exception e){
            LOG.warn("Unable to get config Map for <restClientName>.niws.client."
                            + dynamicPropPrefix + " prefix"); 
        }
        return map;
    }
    
    Map<String, HttpVerbUriRegexPropertyValue> getNIWSStatsConfigMap() {
            return getDynamicPropMap("NIWSStats");
    }

    Map<String, HttpVerbUriRegexPropertyValue> getCacheConfigMap() {
        return getDynamicPropMap("ResponseCache");
    }

    public Map<String, HttpVerbUriRegexPropertyValue> getMethodURIConfigMap() {
        return getDynamicPropMap("MethodURI");
    }
    /**
     * Listen to changes in properties for NIWS
     * @author stonse
     *
     */
    private static class NiwsConfigListener extends AbstractDynamicPropertyListener {
        
        private String getClientNameFromConfig(String name) {
            for (String prefix: DYNAMIC_PROPERTY_PREFIX) {
                if (name.contains(PROPERTY_NAMESPACE + "." + prefix)) {
                    return name.substring(0,name.indexOf(PROPERTY_NAMESPACE + "." + prefix) - 1);
                }
            }
            return null;
        }
        
        @Override
        public void handlePropertyEvent(String name, Object value,
                EventType eventType) {
            try {
                String clientName = getClientNameFromConfig(name);

                if (clientName != null) {
                    String niwsPropertyPrefix = clientName + "."
                            + PROPERTY_NAMESPACE;
                    for (String prefix : DYNAMIC_PROPERTY_PREFIX) {
                        String configPrefix = niwsPropertyPrefix + "." + prefix
                                + ".";
                        if (name != null && name.startsWith(configPrefix)) {
                            Map<String, HttpVerbUriRegexPropertyValue> aliasRuleMapForClient = dynamicConfigMap
                                    .get(prefix).get(clientName);
                            if (aliasRuleMapForClient == null) {
                                // no map exists so far, create one
                                aliasRuleMapForClient = new ConcurrentHashMap<String, HttpVerbUriRegexPropertyValue>();
                                Map<String, HttpVerbUriRegexPropertyValue> prev = dynamicConfigMap.get(prefix).putIfAbsent(clientName,
                                        aliasRuleMapForClient);
                                if (prev != null) {
                                	aliasRuleMapForClient = prev;
                                }
                            }

                            String alias = name.substring(configPrefix.length());
                            if (alias != null) {
                                alias = alias.trim();
                                switch (eventType) {
                                case CLEAR:
                                    aliasRuleMapForClient.remove(alias);
                                    break;
                                case ADD:
                                case SET:
                                    if (value != null) {
                                        aliasRuleMapForClient.put(alias,
                                                HttpVerbUriRegexPropertyValue
                                                        .getVerbUriRegex(value
                                                                .toString()));
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                LOG.warn("Unexpected error when checking for dynamic Rest "
                        + "Client property updates", t);
            }
        }
    }
    
    static void setProperty(Properties props, String restClientName, String key, String value){
        props.setProperty( getInstancePropName(restClientName, key), value);
    }

    public static String getInstancePropName(String restClientName,
            IClientConfigKey configKey) {
        return getInstancePropName(restClientName, configKey.key());
    }

    public static String getInstancePropName(String restClientName, String key) {
        return restClientName + "." + PROPERTY_NAMESPACE + "."
                + key;
    }
    
    public static IClientConfig getNamedConfig(String name) {
        NiwsClientConfig config = namedConfig.get(name);
        if (config != null) {
            return config;
        } else {
            config = getConfigWithDefaultProperties();
            config.loadProperties(name);
            NiwsClientConfig old = namedConfig.put(name, config);
            if (old != null) {
                config = old;
            }
            return config;
        }
    }
}
