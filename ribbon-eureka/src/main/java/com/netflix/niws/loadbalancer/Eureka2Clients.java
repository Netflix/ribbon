package com.netflix.niws.loadbalancer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.client.EurekaInterestClient;

/**
 * Singleton used by {@link Eureka2EnabledNIWSServerList} to get its Eureka2 client.
 * This is a workaround for sharing single Eureka2 client, as {@link com.netflix.loadbalancer.ServerList} instance
 * creation is done with reflection, with no support for injection.
 *
 * @author Tomasz Bak
 */
public final class Eureka2Clients {

    private static final AtomicReference<EurekaInterestClient> interestClient =
            new AtomicReference<EurekaInterestClient>();
    private static final AtomicBoolean useEureka2 = new AtomicBoolean();

    private Eureka2Clients() {
    }

    /**
     * In case both Eureka1 and Eureka2 clients are available, indicate which one should be used.
     * This flag is read by {@link CompositeEurekaEnabledNIWSServerList} to decide to which client discard
     * the request.
     */
    public static boolean isUseEureka2() {
        return useEureka2.get();
    }

    public static boolean setUseEureka2(boolean useEureka2) {
        return Eureka2Clients.useEureka2.getAndSet(useEureka2);
    }

    public static EurekaInterestClient getInterestClient() {
        return interestClient.get();
    }

    public static EurekaInterestClient setInterestClient(EurekaInterestClient interestClient) {
        return Eureka2Clients.interestClient.getAndSet(interestClient);
    }
}
