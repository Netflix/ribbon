package com.netflix.client.config;

import com.netflix.client.VipAddressResolver;

import java.util.concurrent.TimeUnit;

/**
 * This class only exists to support code that depends on these constants that are deprecated now that defaults have
 * moved into the key.
 */
@Deprecated
public abstract class AbstractDefaultClientConfigImpl extends ReloadableClientConfig {
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

    @Deprecated
    public static final Boolean DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS = CommonClientConfigKey.OkToRetryOnAllOperations.defaultValue();

    @Deprecated
    public static final Boolean DEFAULT_ENABLE_NIWS_EVENT_LOGGING = Boolean.TRUE;

    @Deprecated
    public static final Boolean DEFAULT_IS_CLIENT_AUTH_REQUIRED = Boolean.FALSE;

    private volatile VipAddressResolver vipResolver = null;

    protected AbstractDefaultClientConfigImpl(PropertyResolver resolver) {
        super(resolver);
    }

    public void setVipAddressResolver(VipAddressResolver resolver) {
        this.vipResolver = resolver;
    }

    public VipAddressResolver getResolver() {
        return vipResolver;
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "DC_DOUBLECHECK")
    private VipAddressResolver getVipAddressResolver() {
        if (vipResolver == null) {
            synchronized (this) {
                if (vipResolver == null) {
                    try {
                        vipResolver = (VipAddressResolver) Class
                                .forName(getOrDefault(CommonClientConfigKey.VipAddressResolverClassName))
                                .newInstance();
                    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                        throw new RuntimeException("Cannot instantiate VipAddressResolver", e);
                    }
                }
            }
        }
        return vipResolver;
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


}
