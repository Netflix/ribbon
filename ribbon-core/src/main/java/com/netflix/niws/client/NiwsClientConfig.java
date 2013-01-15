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
import com.netflix.niws.VipAddressResolver;

/**
 * Class that holds the NIWS Client Configuration to be used for <code>{@link RestClient}</code>)
 * @author Sudhir Tonse <stonse@netflix.com>
 *
 */
public class NiwsClientConfig {

    public static final Boolean DEFAULT_PRIORITIZE_VIP_ADDRESS_BASED_SERVERS = Boolean.TRUE;

    public static final String DEFAULT_NFLOADBALANCER_PING_CLASSNAME = "com.netflix.niws.client.NIWSDiscoveryPing";

    public static final String DEFAULT_NFLOADBALANCER_RULE_CLASSNAME = com.netflix.niws.client.AvailabilityFilteringRule.class.getName();

    public static final String DEFAULT_NFLOADBALANCER_CLASSNAME = com.netflix.niws.client.ZoneAwareNIWSDiscoveryLoadBalancer.class.getName();
    
    public static final String DEFAULT_CLIENT_CLASSNAME = "com.netflix.niws.client.http.SimpleJerseyClient";
    
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
    
    public static int DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS = 30*1000; // every half minute (30 secs)
    
    public static int DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS = 30*1000; // all connections idle for 30 secs


    
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
            for (NiwsClientConfigKey niwsKey: NiwsClientConfigKey.values()) {
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
        config.putBooleanProperty(NiwsClientConfigKey.UseHttpClient4, DEFAULT_USE_HTTP_CLIENT4);
        config.putIntegerProperty(NiwsClientConfigKey.MaxHttpConnectionsPerHost, Integer.valueOf(DEFAULT_MAX_HTTP_CONNECTIONS_PER_HOST));
        config.putIntegerProperty(NiwsClientConfigKey.MaxTotalHttpConnections, Integer.valueOf(DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS));
        config.putIntegerProperty(NiwsClientConfigKey.ConnectTimeout, Integer.valueOf(DEFAULT_CONNECT_TIMEOUT));
        config.putIntegerProperty(NiwsClientConfigKey.ConnectionManagerTimeout, Integer.valueOf(DEFAULT_CONNECTION_MANAGER_TIMEOUT));
        config.putIntegerProperty(NiwsClientConfigKey.ReadTimeout, Integer.valueOf(DEFAULT_READ_TIMEOUT));
        config.putIntegerProperty(NiwsClientConfigKey.MaxAutoRetries, Integer.valueOf(DEFAULT_MAX_AUTO_RETRIES));
        config.putIntegerProperty(NiwsClientConfigKey.MaxAutoRetriesNextServer, Integer.valueOf(DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER));
        config.putBooleanProperty(NiwsClientConfigKey.OkToRetryOnAllOperations, DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS);
        config.putBooleanProperty(NiwsClientConfigKey.EnableNIWSEventLogging, DEFAULT_ENABLE_NIWS_EVENT_LOGGING);
        config.putFloatProperty(NiwsClientConfigKey.PercentageNIWSEventLogged, Float.valueOf(DEFAULT_PERCENTAGE_NIWS_EVENT_LOGGED));  // 0=only log what the calling context suggests
        config.putBooleanProperty(NiwsClientConfigKey.FollowRedirects, DEFAULT_FOLLOW_REDIRECTS);
        config.putBooleanProperty(NiwsClientConfigKey.ConnectionPoolCleanerTaskEnabled, DEFAULT_CONNECTION_POOL_CLEANER_TASK_ENABLED); // default is true for RestClient
        config.putIntegerProperty(NiwsClientConfigKey.ConnIdleEvictTimeMilliSeconds,
            Integer.valueOf(DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS));
        config.putIntegerProperty(NiwsClientConfigKey.ConnectionCleanerRepeatInterval,
            Integer.valueOf(DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS));
        config.putBooleanProperty(NiwsClientConfigKey.EnableGZIPContentEncodingFilter, DEFAULT_ENABLE_GZIP_CONTENT_ENCODING_FILTER);
        config.putBooleanProperty(NiwsClientConfigKey.EnableRequestThrottling, DEFAULT_ENABLE_REQUEST_THROTTLING);
        config.putIntegerProperty(NiwsClientConfigKey.RequestThrottlingWindowInMSecs, Integer.valueOf(DEFAULT_REQUEST_THROTTLING_WINDOW_IN_MILLIS));
        config.putIntegerProperty(NiwsClientConfigKey.MaxRequestsAllowedPerWindow, Integer.valueOf(DEFAULT_MAX_REQUESTS_ALLOWED_PER_WINDOW));
        String proxyHost = ConfigurationManager.getConfigInstance().getString(getDefaultPropName(NiwsClientConfigKey.ProxyHost.key()));
        if (proxyHost != null && proxyHost.length() > 0) {
            config.setProperty(NiwsClientConfigKey.ProxyHost, proxyHost);
        }
        Integer proxyPort = ConfigurationManager
                .getConfigInstance()
                .getInteger(
                        getDefaultPropName(NiwsClientConfigKey.ProxyPort),
                        (Integer.MIN_VALUE + 1)); // + 1 just to avoid potential clash with user setting
        if (proxyPort != (Integer.MIN_VALUE + 1)) {
            config.setProperty(NiwsClientConfigKey.ProxyPort, proxyPort);
        }
        config.putIntegerProperty(NiwsClientConfigKey.Port, Integer.valueOf(DEFAULT_PORT));
        config.putBooleanProperty(NiwsClientConfigKey.EnablePrimeConnections, DEFAULT_ENABLE_PRIME_CONNECTIONS);
        config.putIntegerProperty(NiwsClientConfigKey.MaxRetriesPerServerPrimeConnection, Integer.valueOf(DEFAULT_MAX_RETRIES_PER_SERVER_PRIME_CONNECTION));
        config.putIntegerProperty(NiwsClientConfigKey.MaxTotalTimeToPrimeConnections, Integer.valueOf(DEFAULT_MAX_TOTAL_TIME_TO_PRIME_CONNECTIONS));
        config.putStringProperty(NiwsClientConfigKey.PrimeConnectionsURI, DEFAULT_PRIME_CONNECTIONS_URI);
        config.putIntegerProperty(NiwsClientConfigKey.PoolMinThreads, Integer.valueOf(DEFAULT_POOL_MIN_THREADS));
        config.putIntegerProperty(NiwsClientConfigKey.PoolMaxThreads, Integer.valueOf(DEFAULT_POOL_MAX_THREADS));
        config.putLongProperty(NiwsClientConfigKey.PoolKeepAliveTime, Long.valueOf(DEFAULT_POOL_KEEP_ALIVE_TIME));
        config.putTimeUnitProperty(NiwsClientConfigKey.PoolKeepAliveTimeUnits,DEFAULT_POOL_KEEP_ALIVE_TIME_UNITS);
        config.putBooleanProperty(NiwsClientConfigKey.EnableZoneAffinity, DEFAULT_ENABLE_ZONE_AFFINITY);
        config.putBooleanProperty(NiwsClientConfigKey.EnableZoneExclusivity, DEFAULT_ENABLE_ZONE_EXCLUSIVITY);
        config.putStringProperty(NiwsClientConfigKey.ClientClassName, DEFAULT_CLIENT_CLASSNAME);
        config.putStringProperty(NiwsClientConfigKey.NFLoadBalancerClassName, DEFAULT_NFLOADBALANCER_CLASSNAME);
        config.putStringProperty(NiwsClientConfigKey.NFLoadBalancerRuleClassName, DEFAULT_NFLOADBALANCER_RULE_CLASSNAME);
        config.putStringProperty(NiwsClientConfigKey.NFLoadBalancerPingClassName, DEFAULT_NFLOADBALANCER_PING_CLASSNAME);
        config.putBooleanProperty(NiwsClientConfigKey.PrioritizeVipAddressBasedServers, DEFAULT_PRIORITIZE_VIP_ADDRESS_BASED_SERVERS);
        config.putBooleanProperty(NiwsClientConfigKey.EnableNIWSStats, DEFAULT_ENABLE_NIWSSTATS);
        config.putBooleanProperty(NiwsClientConfigKey.EnableNIWSErrorStats, DEFAULT_ENABLE_NIWSERRORSTATS);
        config.putFloatProperty(NiwsClientConfigKey.MinPrimeConnectionsRatio, DEFAULT_MIN_PRIME_CONNECTIONS_RATIO);
        config.putBooleanProperty(NiwsClientConfigKey.UseTunnel, Boolean.FALSE);
        config.putStringProperty(NiwsClientConfigKey.PrimeConnectionsClassName, DEFAULT_PRIME_CONNECTIONS_CLASS);
        // putBooleanProperty(NiwsClientConfigKey.PrioritizeIntStack, Boolean.FALSE);
        config.putStringProperty(NiwsClientConfigKey.VipAddressResolverClassName, DEFAULT_VIPADDRESS_RESOLVER_CLASSNAME);
        return config;
    }
    
    
    private void setPropertyInternal(NiwsClientConfigKey propName, Object value) {
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
    private void putIntegerProperty(NiwsClientConfigKey propName, Integer defaultValue) {
        Integer value = ConfigurationManager.getConfigInstance().getInteger(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }

    private void putLongProperty(NiwsClientConfigKey propName, Long defaultValue) {
        Long value = ConfigurationManager.getConfigInstance().getLong(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }
    
    private void putFloatProperty(NiwsClientConfigKey propName, Float defaultValue) {
        Float value = ConfigurationManager.getConfigInstance().getFloat(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }
    
    private void putTimeUnitProperty(NiwsClientConfigKey propName, TimeUnit defaultValue) {
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

    public static String getDefaultPropName(NiwsClientConfigKey propName) {
        return getDefaultPropName(propName.key());
    }

    
    private void putStringProperty(NiwsClientConfigKey propName, String defaultValue) {
        String value = ConfigurationManager.getConfigInstance().getString(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }
    
    private void putBooleanProperty(NiwsClientConfigKey propName, Boolean defaultValue) {
        Boolean value = ConfigurationManager.getConfigInstance().getBoolean(
                getDefaultPropName(propName), defaultValue);
        setPropertyInternal(propName, value);
    }
    
    /**
     * Enum Class to contain properties of the Client
     * @author stonse
     *
     */
    public enum NiwsClientConfigKey {

        //NIWS RestClient related
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
        EnableNIWSEventLogging("EnableNIWSEventLogging"),
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

        public String key() {
            return configKey;
        }
    }

    public void setClientName(String clientName){
        this.clientName  = clientName;
    }

    public String getClientName() {
        return clientName;
    }

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
    
    private VipAddressResolver getVipAddressResolver() {
        if (resolver == null) {
            synchronized (this) {
                if (resolver == null) {
                    try {
                        resolver = (VipAddressResolver) Class.forName(
                                (String) getProperty(NiwsClientConfigKey.VipAddressResolverClassName)).newInstance();
                    } catch (Throwable e) {
                        LOG.error("Cannot instantiate VipAddressResolver", e);
                    }
                }
            }
        }
        return resolver;
        
    }

    public String resolveDeploymentContextbasedVipAddresses(){
        
        String deploymentContextBasedVipAddressesMacro = (String) getProperty(NiwsClientConfigKey.DeploymentContextBasedVipAddresses);
        return getVipAddressResolver().resolve(deploymentContextBasedVipAddressesMacro, this);
        /*
        if (Strings.isNullOrEmpty(deploymentContextBasedVipAddressesMacro)) {
            return null;
        }

        String appName = (String) this.getProperty(NiwsClientConfig.NiwsClientConfigKey.AppName);
        Object p = this.getProperty(NiwsClientConfig.NiwsClientConfigKey.Port);
        Integer port = null;
        if (p instanceof Integer){
            port = Integer.valueOf(""+p);
        }else if (p instanceof String){
            port = Integer.valueOf(p.toString());
        }
        Object sp = this.getProperty(NiwsClientConfig.NiwsClientConfigKey.SecurePort);
        Integer securePort = null;
        if (sp instanceof Integer){
            securePort = Integer.valueOf(""+sp);
        }else if (sp instanceof String){
            securePort = Integer.valueOf(sp.toString());
        }
        String version = (String) this
                .getProperty(NiwsClientConfig.NiwsClientConfigKey.Version);
        boolean prioritizeIntStack = Boolean.valueOf(String.valueOf(this.getProperty(NiwsClientConfigKey.PrioritizeIntStack)));
        return VipAddressUtils.resolveDeploymentContextBasedVipAddresses(deploymentContextBasedVipAddressesMacro, appName, port, securePort, version, prioritizeIntStack);
        */
    }

    public String getAppName(){
        String appName = null;
        Object an = getProperty(NiwsClientConfigKey.AppName);
        if (an!=null){
            appName = "" + an;
        }
        return appName;
    }

    public String getVersion(){
        String version = null;
        Object an = getProperty(NiwsClientConfigKey.Version);
        if (an!=null){
            version = "" + an;
        }
        return version;
    }

    /**
     *  Get the underlying properties cache. Directly adding or changing properties
     *  in the returned properties may cause inconsistencies with dynamic properties.
     *  Instead, use {@link #setProperty(NiwsClientConfigKey, Object)} to set property.
     */
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

    public void setProperty(NiwsClientConfigKey key, Object value){
        setPropertyInternal(key.key(), value);
    }

    public NiwsClientConfig applyOverride(NiwsClientConfig override) {
        if (override == null) {
            return this;
        }
        for (NiwsClientConfigKey key: NiwsClientConfigKey.values()) {
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

    public Object getProperty(NiwsClientConfigKey key){
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

    public Object getProperty(NiwsClientConfigKey key, Object defaultVal){
        Object val = getProperty(key);
        if (val == null){
            return defaultVal;
        }
        return val;
    }

    public static Object getProperty(Map<String, Object> config, NiwsClientConfigKey key, Object defaultVal) {
        Object val = config.get(key.key());
        if (val == null) {
            return defaultVal;
        }
        return val;
    }

    public static Object getProperty(Map<String, Object> config, NiwsClientConfigKey key) {
        return getProperty(config, key, null);
    }

    public boolean isSecure() {
        boolean isSecure = false;
        Object oo = getProperty(NiwsClientConfigKey.IsSecure);
        if (oo!=null && oo instanceof String){
            if(!StringUtils.isBlank((String)oo)){
                String s = (String)oo;
                if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes")){
                    isSecure = true;
                }
            }
        }
        return isSecure;
    }

    public boolean containsProperty(NiwsClientConfigKey key){
        Object oo = getProperty(key);
        return oo!=null? true: false;
    }

    @Override
    public String toString(){
        final StringBuilder sb = new StringBuilder();
        String separator = "";

        sb.append("NiwsClientConfig:");
        for (NiwsClientConfigKey key: NiwsClientConfigKey.values()) {
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
                                    configMapForPrefix.put(getClientName(),
                                            aliasMap);
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
                                dynamicConfigMap.get(prefix).put(clientName,
                                        aliasRuleMapForClient);
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
            NiwsClientConfigKey configKey) {
        return getInstancePropName(restClientName, configKey.key());
    }

    public static String getInstancePropName(String restClientName, String key) {
        return restClientName + "." + PROPERTY_NAMESPACE + "."
                + key;
    }
    
    public static NiwsClientConfig getNamedConfig(String name) {
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
