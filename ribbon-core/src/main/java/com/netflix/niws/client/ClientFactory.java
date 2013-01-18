package com.netflix.niws.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.niws.client.NiwsClientConfig.NiwsClientConfigKey;
import com.netflix.servo.monitor.Monitors;

public class ClientFactory {
    
    private static Map<String, IClient<?,?>> simpleClientMap = new ConcurrentHashMap<String, IClient<?,?>>();
    private static Map<String, ILoadBalancer> namedLBMap = new ConcurrentHashMap<String, ILoadBalancer>();

    private static Logger logger = LoggerFactory.getLogger(ClientFactory.class);
    
    private static synchronized AbstractLoadBalancerAwareClient<?, ?> registerClientFromProperties(String restClientName, NiwsClientConfig niwsClientConfig) { 
        AbstractLoadBalancerAwareClient<?, ?> client = null;
        AbstractLoadBalancer loadBalancer = null;
        try {
            if (simpleClientMap.get(restClientName) != null) {
                throw new NIWSClientException(
                        NIWSClientException.ErrorType.GENERAL,
                        "A Rest Client with this name is already registered. Please use a different name");
            }
            try {
                String clientClassName = (String) niwsClientConfig.getProperty(NiwsClientConfigKey.ClientClassName);
                client = (AbstractLoadBalancerAwareClient<?, ?>) instantiateNiwsConfigAwareClassInstance(clientClassName, niwsClientConfig);
                boolean initializeNFLoadBalancer = Boolean.parseBoolean(niwsClientConfig.getProperty(
                                                NiwsClientConfigKey.InitializeNFLoadBalancer,
                                                NiwsClientConfig.DEFAULT_ENABLE_LOADBALANCER).toString());
                if (initializeNFLoadBalancer) {
                    loadBalancer  = (AbstractLoadBalancer) getNamedLoadBalancer(restClientName);
                }
                client.setLoadBalancer(loadBalancer);
            } catch (Throwable e) {
                String message = "Unable to InitializeAndAssociateNFLoadBalancer set for RestClient:"
                    + restClientName;
                logger.warn(message, e);
                throw new NIWSClientException(NIWSClientException.ErrorType.CONFIGURATION, 
                        message, e);
            }
            simpleClientMap.put(restClientName, client);

            Monitors.registerObject("Client_" + restClientName, client);

        } catch (Exception e) {
            throw new RuntimeException("Error creating client", e);
        }
        logger.info("Client Registered:" + client.toString());
        return client;
    }

    
    public static IClient getNamedClient(String name) {
        NiwsClientConfig niwsClientConfig = NiwsClientConfig.getNamedConfig(name);
        return registerClientFromProperties(name, niwsClientConfig);        
    }
    
    public static ILoadBalancer getNamedLoadBalancer(String name) {
        ILoadBalancer lb = namedLBMap.get(name);
        if (lb != null) {
            return lb;
        } else {
            try {
                lb = registerNamedLoadBalancerFromProperties(name);
            } catch (NIWSClientException e) {
                logger.error("Unable to create load balancer", e);
            }
            return lb;
        }
    }

    private static synchronized AbstractLoadBalancer registerNamedLoadBalancerFromProperties(String name) throws NIWSClientException {
        if (namedLBMap.get(name) != null) {
            throw new NIWSClientException("LoadBalancer for name " + name + " already exists");
        }
        AbstractLoadBalancer lb = null;
        NiwsClientConfig clientConfig = NiwsClientConfig.getNamedConfig(name);
        try {
            String loadBalancerClassName = (String) clientConfig.getProperty(NiwsClientConfigKey.NFLoadBalancerClassName);
            lb = (AbstractLoadBalancer) ClientFactory.instantiateNiwsConfigAwareClassInstance(loadBalancerClassName, clientConfig);                                    
            namedLBMap.put(name, lb);            
            logger.info("Client:" + name
                    + " instantiated a LoadBalancer:" + lb.toString());
            return lb;
        } catch (Exception e) {           
           throw new NIWSClientException("Unable to instantiate/associate LoadBalancer with Client:" + name, e);
        }
    }    

    public static <T extends NiwsClientConfigAware> T instantiateNiwsConfigAwareClassInstance(String className, NiwsClientConfig clientConfig)
            throws InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        T t = null;
        Class<T> clazz = (Class<T>) Class.forName(className);
        t = (T) clazz.newInstance();
        if (t!=null){
            t.initWithNiwsConfig(clientConfig);
        }else{
            logger.warn("Unable to instantiate class:" + className);
        }
        return t;
    }


}
