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

import com.netflix.loadbalancer.Server;

/**
 * Interface that defines operation for priming a connection.
 * 
 * @author awang
 *
 */
public interface IPrimeConnection extends IClientConfigAware {

	/**
	 * Sub classes should implement protocol specific operation to connect
	 * to a server.
	 * 
	 * @param server Server to connect
	 * @param uriPath URI to use in server connection
	 * @return if the priming is successful
	 * @throws Exception Any network errors
	 */
    public boolean connect(Server server, String uriPath) throws Exception;

}
