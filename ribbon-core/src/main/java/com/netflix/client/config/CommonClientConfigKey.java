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

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class CommonClientConfigKey<T> implements IClientConfigKey<T> {

    public static final String DEFAULT_NAME_SPACE = "ribbon";

    public static final IClientConfigKey<String> AppName = new CommonClientConfigKey<String>("AppName"){};
    
    public static final IClientConfigKey<String> Version = new CommonClientConfigKey<String>("Version"){};
        
    public static final IClientConfigKey<Integer> Port = new CommonClientConfigKey<Integer>("Port", 7001){};
    
    public static final IClientConfigKey<Integer> SecurePort = new CommonClientConfigKey<Integer>("SecurePort", 7001){};
    
    public static final IClientConfigKey<String> VipAddress = new CommonClientConfigKey<String>("VipAddress"){};
    
    public static final IClientConfigKey<Boolean> ForceClientPortConfiguration = new CommonClientConfigKey<Boolean>("ForceClientPortConfiguration"){}; // use client defined port regardless of server advert

    public static final IClientConfigKey<String> DeploymentContextBasedVipAddresses = new CommonClientConfigKey<String>("DeploymentContextBasedVipAddresses"){};
    
    public static final IClientConfigKey<Integer> MaxAutoRetries = new CommonClientConfigKey<Integer>("MaxAutoRetries", 0){};
    
    public static final IClientConfigKey<Integer> MaxAutoRetriesNextServer = new CommonClientConfigKey<Integer>("MaxAutoRetriesNextServer", 1){};
    
    public static final IClientConfigKey<Boolean> OkToRetryOnAllOperations = new CommonClientConfigKey<Boolean>("OkToRetryOnAllOperations", false){};
    
    public static final IClientConfigKey<Boolean> RequestSpecificRetryOn = new CommonClientConfigKey<Boolean>("RequestSpecificRetryOn"){};
    
    public static final IClientConfigKey<Integer> ReceiveBufferSize = new CommonClientConfigKey<Integer>("ReceiveBufferSize"){};
    
    public static final IClientConfigKey<Boolean> EnablePrimeConnections = new CommonClientConfigKey<Boolean>("EnablePrimeConnections", false){};
    
    public static final IClientConfigKey<String> PrimeConnectionsClassName = new CommonClientConfigKey<String>("PrimeConnectionsClassName", "com.netflix.niws.client.http.HttpPrimeConnection"){};
    
    public static final IClientConfigKey<Integer> MaxRetriesPerServerPrimeConnection = new CommonClientConfigKey<Integer>("MaxRetriesPerServerPrimeConnection", 9){};
    
    public static final IClientConfigKey<Integer> MaxTotalTimeToPrimeConnections = new CommonClientConfigKey<Integer>("MaxTotalTimeToPrimeConnections", 30000){};
    
    public static final IClientConfigKey<Float> MinPrimeConnectionsRatio = new CommonClientConfigKey<Float>("MinPrimeConnectionsRatio", 1.0f){};
    
    public static final IClientConfigKey<String> PrimeConnectionsURI = new CommonClientConfigKey<String>("PrimeConnectionsURI", "/"){};
    
    public static final IClientConfigKey<Integer> PoolMaxThreads = new CommonClientConfigKey<Integer>("PoolMaxThreads", 200){};
    
    public static final IClientConfigKey<Integer> PoolMinThreads = new CommonClientConfigKey<Integer>("PoolMinThreads", 1){};
    
    public static final IClientConfigKey<Integer> PoolKeepAliveTime = new CommonClientConfigKey<Integer>("PoolKeepAliveTime", 15 * 60){};
    
    public static final IClientConfigKey<String> PoolKeepAliveTimeUnits = new CommonClientConfigKey<String>("PoolKeepAliveTimeUnits", TimeUnit.SECONDS.toString()){};

    public static final IClientConfigKey<Boolean> EnableConnectionPool = new CommonClientConfigKey<Boolean>("EnableConnectionPool", true) {};
    
    /**
     * Use {@link #MaxConnectionsPerHost}
     */
    @Deprecated    
    public static final IClientConfigKey<Integer> MaxHttpConnectionsPerHost = new CommonClientConfigKey<Integer>("MaxHttpConnectionsPerHost", 50){};
    
    /**
     * Use {@link #MaxTotalConnections}
     */
    @Deprecated
    public static final IClientConfigKey<Integer> MaxTotalHttpConnections = new CommonClientConfigKey<Integer>("MaxTotalHttpConnections", 200){};
    
    public static final IClientConfigKey<Integer> MaxConnectionsPerHost = new CommonClientConfigKey<Integer>("MaxConnectionsPerHost", 50){};
    
    public static final IClientConfigKey<Integer> MaxTotalConnections = new CommonClientConfigKey<Integer>("MaxTotalConnections", 200){};
    
    public static final IClientConfigKey<Boolean> IsSecure = new CommonClientConfigKey<Boolean>("IsSecure"){};
    
    public static final IClientConfigKey<Boolean> GZipPayload = new CommonClientConfigKey<Boolean>("GZipPayload"){};
    
    public static final IClientConfigKey<Integer> ConnectTimeout = new CommonClientConfigKey<Integer>("ConnectTimeout", 2000){};
    
    public static final IClientConfigKey<Integer> BackoffInterval = new CommonClientConfigKey<Integer>("BackoffTimeout", 0){};
    
    public static final IClientConfigKey<Integer> ReadTimeout = new CommonClientConfigKey<Integer>("ReadTimeout", 5000){};
    
    public static final IClientConfigKey<Integer> SendBufferSize = new CommonClientConfigKey<Integer>("SendBufferSize"){};
    
    public static final IClientConfigKey<Boolean> StaleCheckingEnabled = new CommonClientConfigKey<Boolean>("StaleCheckingEnabled", false){};
    
    public static final IClientConfigKey<Integer> Linger = new CommonClientConfigKey<Integer>("Linger", 0){};
    
    public static final IClientConfigKey<Integer> ConnectionManagerTimeout = new CommonClientConfigKey<Integer>("ConnectionManagerTimeout", 2000){};
    
    public static final IClientConfigKey<Boolean> FollowRedirects = new CommonClientConfigKey<Boolean>("FollowRedirects", false){};
    
    public static final IClientConfigKey<Boolean> ConnectionPoolCleanerTaskEnabled = new CommonClientConfigKey<Boolean>("ConnectionPoolCleanerTaskEnabled", true){};
    
    public static final IClientConfigKey<Integer> ConnIdleEvictTimeMilliSeconds = new CommonClientConfigKey<Integer>("ConnIdleEvictTimeMilliSeconds", 30*1000){};
    
    public static final IClientConfigKey<Integer> ConnectionCleanerRepeatInterval = new CommonClientConfigKey<Integer>("ConnectionCleanerRepeatInterval", 30*1000){};
    
    public static final IClientConfigKey<Boolean> EnableGZIPContentEncodingFilter = new CommonClientConfigKey<Boolean>("EnableGZIPContentEncodingFilter", false){};
    
    public static final IClientConfigKey<String> ProxyHost = new CommonClientConfigKey<String>("ProxyHost"){};
    
    public static final IClientConfigKey<Integer> ProxyPort = new CommonClientConfigKey<Integer>("ProxyPort"){};
    
    public static final IClientConfigKey<String> KeyStore = new CommonClientConfigKey<String>("KeyStore"){};
    
    public static final IClientConfigKey<String> KeyStorePassword = new CommonClientConfigKey<String>("KeyStorePassword"){};
    
    public static final IClientConfigKey<String> TrustStore = new CommonClientConfigKey<String>("TrustStore"){};
    
    public static final IClientConfigKey<String> TrustStorePassword = new CommonClientConfigKey<String>("TrustStorePassword"){};
    
    // if this is a secure rest client, must we use client auth too?    
    public static final IClientConfigKey<Boolean> IsClientAuthRequired = new CommonClientConfigKey<Boolean>("IsClientAuthRequired", false){};
    
    public static final IClientConfigKey<String> CustomSSLSocketFactoryClassName = new CommonClientConfigKey<String>("CustomSSLSocketFactoryClassName"){};
     // must host name match name in certificate?
    public static final IClientConfigKey<Boolean> IsHostnameValidationRequired = new CommonClientConfigKey<Boolean>("IsHostnameValidationRequired"){}; 

    // see also http://hc.apache.org/httpcomponents-client-ga/tutorial/html/advanced.html
    public static final IClientConfigKey<Boolean> IgnoreUserTokenInConnectionPoolForSecureClient = new CommonClientConfigKey<Boolean>("IgnoreUserTokenInConnectionPoolForSecureClient"){}; 
    
    // Client implementation
    public static final IClientConfigKey<String> ClientClassName = new CommonClientConfigKey<String>("ClientClassName", "com.netflix.niws.client.http.RestClient"){};

    //LoadBalancer Related
    public static final IClientConfigKey<Boolean> InitializeNFLoadBalancer = new CommonClientConfigKey<Boolean>("InitializeNFLoadBalancer", true){};
    
    public static final IClientConfigKey<String> NFLoadBalancerClassName = new CommonClientConfigKey<String>("NFLoadBalancerClassName", "com.netflix.loadbalancer.ZoneAwareLoadBalancer"){};
    
    public static final IClientConfigKey<String> NFLoadBalancerRuleClassName = new CommonClientConfigKey<String>("NFLoadBalancerRuleClassName", "com.netflix.loadbalancer.AvailabilityFilteringRule"){};
    
    public static final IClientConfigKey<String> NFLoadBalancerPingClassName = new CommonClientConfigKey<String>("NFLoadBalancerPingClassName", "com.netflix.loadbalancer.DummyPing"){};
    
    public static final IClientConfigKey<Integer> NFLoadBalancerPingInterval = new CommonClientConfigKey<Integer>("NFLoadBalancerPingInterval"){};
    
    public static final IClientConfigKey<Integer> NFLoadBalancerMaxTotalPingTime = new CommonClientConfigKey<Integer>("NFLoadBalancerMaxTotalPingTime"){};

    public static final IClientConfigKey<String> NFLoadBalancerStatsClassName = new CommonClientConfigKey<String>("NFLoadBalancerStatsClassName", "com.netflix.loadbalancer.LoadBalancerStats"){};
    
    public static final IClientConfigKey<String> NIWSServerListClassName = new CommonClientConfigKey<String>("NIWSServerListClassName", "com.netflix.loadbalancer.ConfigurationBasedServerList"){};

    public static final IClientConfigKey<String> ServerListUpdaterClassName = new CommonClientConfigKey<String>("ServerListUpdaterClassName", "com.netflix.loadbalancer.PollingServerListUpdater"){};
    
    public static final IClientConfigKey<String> NIWSServerListFilterClassName = new CommonClientConfigKey<String>("NIWSServerListFilterClassName"){};
    
    public static final IClientConfigKey<Integer> ServerListRefreshInterval = new CommonClientConfigKey<Integer>("ServerListRefreshInterval"){};
    
    public static final IClientConfigKey<Boolean> EnableMarkingServerDownOnReachingFailureLimit = new CommonClientConfigKey<Boolean>("EnableMarkingServerDownOnReachingFailureLimit"){};
    
    public static final IClientConfigKey<Integer> ServerDownFailureLimit = new CommonClientConfigKey<Integer>("ServerDownFailureLimit"){};
    
    public static final IClientConfigKey<Integer> ServerDownStatWindowInMillis = new CommonClientConfigKey<Integer>("ServerDownStatWindowInMillis"){};
    
    public static final IClientConfigKey<Boolean> EnableZoneAffinity = new CommonClientConfigKey<Boolean>("EnableZoneAffinity", false){};
    
    public static final IClientConfigKey<Boolean> EnableZoneExclusivity = new CommonClientConfigKey<Boolean>("EnableZoneExclusivity", false){};
    
    public static final IClientConfigKey<Boolean> PrioritizeVipAddressBasedServers = new CommonClientConfigKey<Boolean>("PrioritizeVipAddressBasedServers", true){};
    
    public static final IClientConfigKey<String> VipAddressResolverClassName = new CommonClientConfigKey<String>("VipAddressResolverClassName", "com.netflix.client.SimpleVipAddressResolver"){};
    
    public static final IClientConfigKey<String> TargetRegion = new CommonClientConfigKey<String>("TargetRegion"){};
    
    public static final IClientConfigKey<String> RulePredicateClasses = new CommonClientConfigKey<String>("RulePredicateClasses"){};
    
    public static final IClientConfigKey<String> RequestIdHeaderName = new CommonClientConfigKey<String>("RequestIdHeaderName") {};
    
    public static final IClientConfigKey<Boolean> UseIPAddrForServer = new CommonClientConfigKey<Boolean>("UseIPAddrForServer", false) {};
    
    public static final IClientConfigKey<String> ListOfServers = new CommonClientConfigKey<String>("listOfServers", "") {};

    private static final Set<IClientConfigKey> keys = new HashSet<IClientConfigKey>();
        
    static {
        for (Field f: CommonClientConfigKey.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) //&& Modifier.isPublic(f.getModifiers())
                    && IClientConfigKey.class.isAssignableFrom(f.getType())) {
                try {
                    keys.add((IClientConfigKey) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * @deprecated see {@link #keys()} 
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings
    @Deprecated
    public static IClientConfigKey[] values() {
       return keys().toArray(new IClientConfigKey[0]);
    }

    /**
     * return all the public static keys defined in this class 
     */
    public static Set<IClientConfigKey> keys() {
        return keys;
    }

    public static IClientConfigKey valueOf(final String name) {
        for (IClientConfigKey key: keys()) {
            if (key.key().equals(name)) {
                return key;
            }
        }
        return new IClientConfigKey() {
            @Override
            public String key() {
                return name;
            }

            @Override
            public Class type() {
                return String.class;
            }
        };
    }
    
    private final String configKey;
    private final Class<T> type;
    private T defaultValue;

    @SuppressWarnings("unchecked")
    protected CommonClientConfigKey(String configKey) {
        this(configKey, null);
    }

    protected CommonClientConfigKey(String configKey, T defaultValue) {
        this.configKey = configKey;
        Type superclass = getClass().getGenericSuperclass();
        checkArgument(superclass instanceof ParameterizedType,
                "%s isn't parameterized", superclass);
        Type runtimeType = ((ParameterizedType) superclass).getActualTypeArguments()[0];
        type = (Class<T>) TypeToken.of(runtimeType).getRawType();
        this.defaultValue = defaultValue;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    /* (non-Javadoc)
	 * @see com.netflix.niws.client.ClientConfig#key()
	 */
    @Override
	public String key() {
        return configKey;
    }
    
    @Override
    public String toString() {
        return configKey;
    }

    @Override
    public T defaultValue() { return defaultValue; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommonClientConfigKey<?> that = (CommonClientConfigKey<?>) o;
        return Objects.equals(configKey, that.configKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configKey);
    }
}
