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
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class CommonClientConfigKey<T> implements IClientConfigKey<T> {

    public static final IClientConfigKey<String> AppName = new CommonClientConfigKey<String>("AppName"){};
    
    public static final IClientConfigKey<String> Version = new CommonClientConfigKey<String>("Version"){};
        
    public static final IClientConfigKey<Integer> Port = new CommonClientConfigKey<Integer>("Port"){};
    
    public static final IClientConfigKey<Integer> SecurePort = new CommonClientConfigKey<Integer>("SecurePort"){};
    
    public static final IClientConfigKey<String> VipAddress = new CommonClientConfigKey<String>("VipAddress"){};
    
    public static final IClientConfigKey<Boolean> ForceClientPortConfiguration = new CommonClientConfigKey<Boolean>("ForceClientPortConfiguration"){}; // use client defined port regardless of server advert

    public static final IClientConfigKey<String> DeploymentContextBasedVipAddresses = new CommonClientConfigKey<String>("DeploymentContextBasedVipAddresses"){};
    
    public static final IClientConfigKey<Integer> MaxAutoRetries = new CommonClientConfigKey<Integer>("MaxAutoRetries"){};
    
    public static final IClientConfigKey<Integer> MaxAutoRetriesNextServer = new CommonClientConfigKey<Integer>("MaxAutoRetriesNextServer"){};
    
    public static final IClientConfigKey<Boolean> OkToRetryOnAllOperations = new CommonClientConfigKey<Boolean>("OkToRetryOnAllOperations"){};
    
    public static final IClientConfigKey<Boolean> RequestSpecificRetryOn = new CommonClientConfigKey<Boolean>("RequestSpecificRetryOn"){};
    
    public static final IClientConfigKey<Integer> ReceiveBufferSize = new CommonClientConfigKey<Integer>("ReceiveBufferSize"){};
    
    public static final IClientConfigKey<Boolean> EnablePrimeConnections = new CommonClientConfigKey<Boolean>("EnablePrimeConnections"){};
    
    public static final IClientConfigKey<String> PrimeConnectionsClassName = new CommonClientConfigKey<String>("PrimeConnectionsClassName"){};
    
    public static final IClientConfigKey<Integer> MaxRetriesPerServerPrimeConnection = new CommonClientConfigKey<Integer>("MaxRetriesPerServerPrimeConnection"){};
    
    public static final IClientConfigKey<Integer> MaxTotalTimeToPrimeConnections = new CommonClientConfigKey<Integer>("MaxTotalTimeToPrimeConnections"){};
    
    public static final IClientConfigKey<Float> MinPrimeConnectionsRatio = new CommonClientConfigKey<Float>("MinPrimeConnectionsRatio"){};
    
    public static final IClientConfigKey<String> PrimeConnectionsURI = new CommonClientConfigKey<String>("PrimeConnectionsURI"){};
    
    public static final IClientConfigKey<Integer> PoolMaxThreads = new CommonClientConfigKey<Integer>("PoolMaxThreads"){};
    
    public static final IClientConfigKey<Integer> PoolMinThreads = new CommonClientConfigKey<Integer>("PoolMinThreads"){};
    
    public static final IClientConfigKey<Integer> PoolKeepAliveTime = new CommonClientConfigKey<Integer>("PoolKeepAliveTime"){};
    
    public static final IClientConfigKey<String> PoolKeepAliveTimeUnits = new CommonClientConfigKey<String>("PoolKeepAliveTimeUnits"){};

    public static final IClientConfigKey<Boolean> EnableConnectionPool = new CommonClientConfigKey<Boolean>("EnableConnectionPool") {};
    
    /**
     * Use {@link #MaxConnectionsPerHost}
     */
    @Deprecated    
    public static final IClientConfigKey<Integer> MaxHttpConnectionsPerHost = new CommonClientConfigKey<Integer>("MaxHttpConnectionsPerHost"){};
    
    /**
     * Use {@link #MaxTotalConnections}
     */
    @Deprecated
    public static final IClientConfigKey<Integer> MaxTotalHttpConnections = new CommonClientConfigKey<Integer>("MaxTotalHttpConnections"){};
    
    public static final IClientConfigKey<Integer> MaxConnectionsPerHost = new CommonClientConfigKey<Integer>("MaxConnectionsPerHost"){};
    
    public static final IClientConfigKey<Integer> MaxTotalConnections = new CommonClientConfigKey<Integer>("MaxTotalConnections"){};
    
    public static final IClientConfigKey<Boolean> IsSecure = new CommonClientConfigKey<Boolean>("IsSecure"){};
    
    public static final IClientConfigKey<Boolean> GZipPayload = new CommonClientConfigKey<Boolean>("GZipPayload"){};
    
    public static final IClientConfigKey<Integer> ConnectTimeout = new CommonClientConfigKey<Integer>("ConnectTimeout"){};
    
    public static final IClientConfigKey<Integer> BackoffInterval = new CommonClientConfigKey<Integer>("BackoffTimeout"){};
    
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

    public static final IClientConfigKey<String> ServerListUpdaterClassName = new CommonClientConfigKey<String>("ServerListUpdaterClassName"){};
    
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
    
    public static final IClientConfigKey<String> RequestIdHeaderName = new CommonClientConfigKey<String>("RequestIdHeaderName") {};
    
    public static final IClientConfigKey<Boolean> UseIPAddrForServer = new CommonClientConfigKey<Boolean>("UseIPAddrForServer") {};
    
    public static final IClientConfigKey<String> ListOfServers = new CommonClientConfigKey<String>("listOfServers") {};

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
    
    @SuppressWarnings("unchecked")
    protected CommonClientConfigKey(String configKey) {
        this.configKey = configKey;
        Type superclass = getClass().getGenericSuperclass();
        checkArgument(superclass instanceof ParameterizedType,
            "%s isn't parameterized", superclass);
        Type runtimeType = ((ParameterizedType) superclass).getActualTypeArguments()[0];
        type = (Class<T>) TypeToken.of(runtimeType).getRawType();
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
}
