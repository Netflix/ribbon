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

import java.util.List;

import com.netflix.client.IClientConfigAware;
import com.netflix.client.ClientException;
import com.netflix.client.config.IClientConfig;

/**
 * The class that defines how a list of servers are obtained, updated and filtered for use by NIWS
 * @author stonse
 *
 */
public abstract class AbstractServerList<T extends Server> implements ServerList<T>, IClientConfigAware {   
           
    /**
     * This will be called ONLY ONCE to obtain the Filter class instance
     * Concrete imple
     * @param niwsClientConfig
     * @return
     */
    public AbstractServerListFilter<T> getFilterImpl(IClientConfig niwsClientConfig) throws ClientException{
        return new AbstractServerListFilter<T>(){

            @Override
            public List<T> getFilteredListOfServers(List<T> servers) {
                return servers;
            }
        };

    }
}
