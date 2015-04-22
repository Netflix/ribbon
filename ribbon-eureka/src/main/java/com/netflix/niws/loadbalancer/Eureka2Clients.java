package com.netflix.niws.loadbalancer;

import com.netflix.eureka2.client.EurekaInterestClient;

/**
 * Singleton used by {@link Eureka2EnabledNIWSServerList} to get its Eureka2 client.
 * This is a workaround for sharing single Eureka2 client, as {@link com.netflix.loadbalancer.ServerList} instance
 * creation is done with reflection, with no support for injection.
 *
 * @author Tomasz Bak
 */
public final class Eureka2Clients {

    private static volatile EurekaInterestClient interestClient;
    private static volatile boolean useEureka2;

    private Eureka2Clients() {
    }

    /**
     * In case both Eureka1 and Eureka2 clients are available, indicate which one should be used.
     * This flag is read by {@link CompositeEurekaEnabledNIWSServerList} to decide to which client discard
     * the request.
     */
    public static boolean isUseEureka2() {
        return useEureka2;
    }

    public static boolean setUseEureka2(boolean useEureka2) {
        boolean lastValue = Eureka2Clients.useEureka2;
        Eureka2Clients.useEureka2 = useEureka2;
        return lastValue;
    }

    public static EurekaInterestClient getInterestClient() {
        return interestClient;
    }

    public static EurekaInterestClient setInterestClient(EurekaInterestClient interestClient) {
        EurekaInterestClient lastValue = Eureka2Clients.interestClient;
        Eureka2Clients.interestClient = interestClient;
        return lastValue;
    }
}
