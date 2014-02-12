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
package com.netflix.loadbalancer;

import com.netflix.client.ClientFactory;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;


/**
 * The class includes an API to create a filter to be use by load balancer
 * to filter the servers returned from {@link #getUpdatedListOfServers()} or {@link #getInitialListOfServers()}.
 *
 */
public abstract class AbstractServerList<T extends Server> implements ServerList<T>, IClientConfigAware {   
     
    
    /**
     * Get a ServerListFilter instance. It uses {@link ClientFactory#instantiateInstanceWithClientConfig(String, IClientConfig)}
     * which in turn uses reflection to initialize the filter instance. 
     * The filter class name is determined by the value of {@link CommonClientConfigKey#NIWSServerListFilterClassName}
     * in the {@link IClientConfig}. The default implementation is {@link ZoneAffinityServerListFilter}.
     */
    public AbstractServerListFilter<T> getFilterImpl(IClientConfig niwsClientConfig) throws ClientException{
        try {
            String niwsServerListFilterClassName = niwsClientConfig
                    .getProperty(
                            CommonClientConfigKey.NIWSServerListFilterClassName,
                            ZoneAffinityServerListFilter.class.getName())
                    .toString();

            AbstractServerListFilter<T> abstractNIWSServerListFilter = 
                    (AbstractServerListFilter<T>) ClientFactory.instantiateInstanceWithClientConfig(niwsServerListFilterClassName, niwsClientConfig);
            return abstractNIWSServerListFilter;
        } catch (Throwable e) {
            throw new ClientException(
                    ClientException.ErrorType.CONFIGURATION,
                    "Unable to get an instance of CommonClientConfigKey.NIWSServerListFilterClassName. Configured class:"
                            + niwsClientConfig
                                    .getProperty(CommonClientConfigKey.NIWSServerListFilterClassName), e);
        }
    }
}
