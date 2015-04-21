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
    private static boolean peferEureka2;

    private Eureka2Clients() {
    }

    /**
     * In case both Eureka1 and Eureka2 clients are available, indicate which one is a preferred one,
     * This flag is read by {@link CompositeEurekaEnabledNIWSServerList} to decide to which client discard
     * the request.
     */
    public static boolean isPreferEureka2() {
        return peferEureka2;
    }

    public static void setPreferEureka2(boolean peferEureka2) {
        Eureka2Clients.peferEureka2 = peferEureka2;
    }

    public static EurekaInterestClient getInterestClient() {
        if (interestClient == null) {
            throw new IllegalStateException("Eureka2 client not set");
        }
        return interestClient;
    }

    public static void setInterestClient(EurekaInterestClient interestClient) {
        Eureka2Clients.interestClient = interestClient;
    }
}
