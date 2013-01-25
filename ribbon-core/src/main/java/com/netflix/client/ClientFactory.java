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
package com.netflix.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.servo.monitor.Monitors;

public class ClientFactory {
    
    private static Map<String, IClient<?,?>> simpleClientMap = new ConcurrentHashMap<String, IClient<?,?>>();
    private static Map<String, ILoadBalancer> namedLBMap = new ConcurrentHashMap<String, ILoadBalancer>();
    private static ConcurrentHashMap<String, IClientConfig> namedConfig = new ConcurrentHashMap<String, IClientConfig>();
    
    private static Logger logger = LoggerFactory.getLogger(ClientFactory.class);
    
    public static synchronized IClient<?, ?> registerClientFromProperties(String restClientName, IClientConfig niwsClientConfig) { 
        IClient<?, ?> client = null;
        ILoadBalancer loadBalancer = null;
        try {
            if (simpleClientMap.get(restClientName) != null) {
                throw new ClientException(
                        ClientException.ErrorType.GENERAL,
                        "A Rest Client with this name is already registered. Please use a different name");
            }
            try {
                String clientClassName = (String) niwsClientConfig.getProperty(CommonClientConfigKey.ClientClassName);
                client = (IClient<?, ?>) instantiateInstanceWithClientConfig(clientClassName, niwsClientConfig);
                boolean initializeNFLoadBalancer = Boolean.parseBoolean(niwsClientConfig.getProperty(
                                                CommonClientConfigKey.InitializeNFLoadBalancer, DefaultClientConfigImpl.DEFAULT_ENABLE_LOADBALANCER).toString());
                if (initializeNFLoadBalancer) {
                    loadBalancer  = registerNamedLoadBalancerFromclientConfig(restClientName, niwsClientConfig);
                }
                if (client instanceof AbstractLoadBalancerAwareClient) {
                	((AbstractLoadBalancerAwareClient) client).setLoadBalancer(loadBalancer);
                }
            } catch (Throwable e) {
                String message = "Unable to InitializeAndAssociateNFLoadBalancer set for RestClient:"
                    + restClientName;
                logger.warn(message, e);
                throw new ClientException(ClientException.ErrorType.CONFIGURATION, 
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

    
    public static synchronized IClient getNamedClient(String name) {
        IClientConfig niwsClientConfig = getNamedConfig(name);
        return registerClientFromProperties(name, niwsClientConfig);        
    }
    
    public static synchronized IClient getNamedClient(String name, Class<? extends IClientConfig> configClass) throws ClientException {
    	IClientConfig config = getNamedConfig(name, configClass);
        return registerClientFromProperties(name, config);
    }
    
    public static synchronized ILoadBalancer getNamedLoadBalancer(String name) {
    	return getNamedLoadBalancer(name, DefaultClientConfigImpl.class);
    }
    
    public static synchronized ILoadBalancer getNamedLoadBalancer(String name, Class<? extends IClientConfig> configClass) {
        ILoadBalancer lb = namedLBMap.get(name);
        if (lb != null) {
            return lb;
        } else {
            try {
                lb = registerNamedLoadBalancerFromProperties(name, configClass);
            } catch (ClientException e) {
                logger.error("Unable to create load balancer", e);
            }
            return lb;
        }
    }

    public static ILoadBalancer registerNamedLoadBalancerFromclientConfig(String name, IClientConfig clientConfig) throws ClientException {
    	ILoadBalancer lb = null;
        try {
            String loadBalancerClassName = (String) clientConfig.getProperty(CommonClientConfigKey.NFLoadBalancerClassName);
            lb = (ILoadBalancer) ClientFactory.instantiateInstanceWithClientConfig(loadBalancerClassName, clientConfig);                                    
            namedLBMap.put(name, lb);            
            logger.info("Client:" + name
                    + " instantiated a LoadBalancer:" + lb.toString());
            return lb;
        } catch (Exception e) {           
           throw new ClientException("Unable to instantiate/associate LoadBalancer with Client:" + name, e);
        }    	
    }
    
    public static synchronized ILoadBalancer registerNamedLoadBalancerFromProperties(String name, Class<? extends IClientConfig> configClass) throws ClientException {
        if (namedLBMap.get(name) != null) {
            throw new ClientException("LoadBalancer for name " + name + " already exists");
        }
        IClientConfig clientConfig = getNamedConfig(name, configClass);
        return registerNamedLoadBalancerFromclientConfig(name, clientConfig);
    }    

    @SuppressWarnings("unchecked")
	public static Object instantiateInstanceWithClientConfig(String className, IClientConfig clientConfig) 
    		throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    	Class clazz = Class.forName(className);
    	if (IClientConfigAware.class.isAssignableFrom(clazz)) {
    		IClientConfigAware obj = (IClientConfigAware) clazz.newInstance();
    		obj.initWithNiwsConfig(clientConfig);
    		return obj;
    	} else {
    		try {
    		    if (clazz.getConstructor(IClientConfig.class) != null) {
    		    	return clazz.getConstructor(IClientConfig.class).newInstance(clientConfig);
    		    }
    		} catch (Throwable e) { // NOPMD   			
    		}    		
    	}
    	logger.warn("Class " + className + " neither implements IClientConfigAware nor provides a constructor with IClientConfig as the parameter. Only default constructor will be used.");
    	return clazz.newInstance();
    }
    
    public static IClientConfig getNamedConfig(String name) {
        return 	getNamedConfig(name, DefaultClientConfigImpl.class);
    }
    
    public static IClientConfig getNamedConfig(String name, Class<? extends IClientConfig> clientConfigClass) {
    	IClientConfig config = namedConfig.get(name);
        if (config != null) {
            return config;
        } else {
        	try {
                config = (IClientConfig) clientConfigClass.newInstance();
                config.loadProperties(name);
        	} catch (Throwable e) {
        		logger.error("Unable to create client config instance", e);
        		return null;
        	}
            config.loadProperties(name);
            IClientConfig old = namedConfig.putIfAbsent(name, config);
            if (old != null) {
                config = old;
            }
            return config;
        }
    }
}
