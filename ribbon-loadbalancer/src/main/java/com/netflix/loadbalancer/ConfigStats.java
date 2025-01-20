package com.netflix.loadbalancer;

import com.google.common.base.Preconditions;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.UnboxedIntProperty;
import com.netflix.client.IClientConfigAware;

public class ConfigStats implements IClientConfigAware{
    public static final IClientConfigKey<Integer> ACTIVE_REQUESTS_COUNT_TIMEOUT = new CommonClientConfigKey<Integer>(
            "niws.loadbalancer.serverStats.activeRequestsCount.effectiveWindowSeconds", 60 * 10) {};

    public static final IClientConfigKey<Integer> CONNECTION_FAILURE_COUNT_THRESHOLD = new CommonClientConfigKey<Integer>(
            "niws.loadbalancer.%s.connectionFailureCountThreshold", 3) {};

    public static final IClientConfigKey<Integer> CIRCUIT_TRIP_TIMEOUT_FACTOR_SECONDS = new CommonClientConfigKey<Integer>(
            "niws.loadbalancer.%s.circuitTripTimeoutFactorSeconds", 10) {};

    public static final IClientConfigKey<Integer> CIRCUIT_TRIP_MAX_TIMEOUT_SECONDS = new CommonClientConfigKey<Integer>(
            "niws.loadbalancer.%s.circuitTripMaxTimeoutSeconds", 30) {};

    public static final IClientConfigKey<Integer> DEFAULT_CONNECTION_FAILURE_COUNT_THRESHOLD = new CommonClientConfigKey<Integer>(
            "niws.loadbalancer.default.connectionFailureCountThreshold", 3) {};

    public static final IClientConfigKey<Integer> DEFAULT_CIRCUIT_TRIP_TIMEOUT_FACTOR_SECONDS = new CommonClientConfigKey<Integer>(
            "niws.loadbalancer.default.circuitTripTimeoutFactorSeconds", 10) {};

    public static final IClientConfigKey<Integer> DEFAULT_CIRCUIT_TRIP_MAX_TIMEOUT_SECONDS = new CommonClientConfigKey<Integer>(
            "niws.loadbalancer.default.circuitTripMaxTimeoutSeconds", 30) {};

    private UnboxedIntProperty connectionFailureThreshold = new UnboxedIntProperty(CONNECTION_FAILURE_COUNT_THRESHOLD.defaultValue());

    private UnboxedIntProperty circuitTrippedTimeoutFactor = new UnboxedIntProperty(CIRCUIT_TRIP_TIMEOUT_FACTOR_SECONDS.defaultValue());

    private UnboxedIntProperty maxCircuitTrippedTimeout = new UnboxedIntProperty(CIRCUIT_TRIP_MAX_TIMEOUT_SECONDS.defaultValue());

    private UnboxedIntProperty activeRequestsCountTimeout = new UnboxedIntProperty(ACTIVE_REQUESTS_COUNT_TIMEOUT.defaultValue());

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        String name = clientConfig.getClientName();
        Preconditions.checkArgument(name != null, "IClientConfig#getClientName() must not be null");
        this.connectionFailureThreshold = new UnboxedIntProperty(
                clientConfig.getGlobalProperty(CONNECTION_FAILURE_COUNT_THRESHOLD.format(name))
                        .fallbackWith(clientConfig.getGlobalProperty(DEFAULT_CONNECTION_FAILURE_COUNT_THRESHOLD))
        );
        this.circuitTrippedTimeoutFactor = new UnboxedIntProperty(
                clientConfig.getGlobalProperty(CIRCUIT_TRIP_TIMEOUT_FACTOR_SECONDS.format(name))
                        .fallbackWith(clientConfig.getGlobalProperty(DEFAULT_CIRCUIT_TRIP_TIMEOUT_FACTOR_SECONDS))
        );
        this.maxCircuitTrippedTimeout = new UnboxedIntProperty(
                clientConfig.getGlobalProperty(CIRCUIT_TRIP_MAX_TIMEOUT_SECONDS.format(name))
                        .fallbackWith(clientConfig.getGlobalProperty(DEFAULT_CIRCUIT_TRIP_MAX_TIMEOUT_SECONDS))
        );
        this.activeRequestsCountTimeout = new UnboxedIntProperty(
                clientConfig.getGlobalProperty(ACTIVE_REQUESTS_COUNT_TIMEOUT));
    }

    int getConnectionFailureThreshold() {
        return connectionFailureThreshold.get();
    }

    int getCircuitTrippedTimeoutFactor() {
        return circuitTrippedTimeoutFactor.get();
    }

    int getMaxCircuitTrippedTimeout() {
        return maxCircuitTrippedTimeout.get();
    }

    int getActiveRequestsCountTimeout() { return activeRequestsCountTimeout.get(); }

}