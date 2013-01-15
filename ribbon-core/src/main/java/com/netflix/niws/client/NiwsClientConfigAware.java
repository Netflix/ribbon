package com.netflix.niws.client;

/**
 * There are multiple classes (and components) that need access to the configuration.
 * Its easier to do this by using NiwsClientConfig as the object that carries these configurations
 * and to define a common interface that components that need this can implement and hence be aware of.
 * For e.g. AbstractNIWSLoadBalancer and other family of LB related components make use of this facility
 * @author stonse
 *
 */
public interface NiwsClientConfigAware {

    /**
     * Concrete implementation should implement this method so that the configuration set via 
     * NIWSClientConfig (which in turn were set via NC properties) will be taken into consideration
     * @param niwsClientConfig
     */
    public abstract void initWithNiwsConfig(NiwsClientConfig niwsClientConfig);
    
}
