package com.netflix.loadbalancer;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicDoubleProperty;
import com.netflix.config.DynamicPropertyFactory;

/**
 * Shared config holder for different impls of the ZoneAwareLoadBalancer
 */
final class ZoneAwareLoadBalancerConfig {
    private static final DynamicBooleanProperty ENABLED =
            DynamicPropertyFactory.getInstance().getBooleanProperty("ZoneAwareNIWSDiscoveryLoadBalancer.enabled", true);

    static boolean getEnabled() {
        return ENABLED.get();
    }

    static DynamicDoubleProperty newTriggeringLoadProperty(String name) {
        return DynamicPropertyFactory.getInstance().getDoubleProperty(
                "ZoneAwareNIWSDiscoveryLoadBalancer." + name + ".triggeringLoadPerServerThreshold", 0.2d);
    }

    static DynamicDoubleProperty newTriggeringBlackoutPercentageProperty(String name) {
        return DynamicPropertyFactory.getInstance().getDoubleProperty(
                "ZoneAwareNIWSDiscoveryLoadBalancer." + name + ".avoidZoneWithBlackoutPercetage", 0.99999d);
    }
}
