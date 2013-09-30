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
package com.netflix.client.config;

import java.util.Map;

/**
 * Defines the client configuration used by various APIs to initialize clients or load balancers.
 * 
 * @author awang
 *
 */

public interface IClientConfig {
	
	public String getClientName();
		
	public String getNameSpace();

	/**
	 * Load the properties for a given client and/or load balancer. 
	 * @param clientName
	 */
	public void loadProperties(String clientName);

	public Map<String, Object> getProperties();

	public void setProperty(IClientConfigKey key, Object value);

	public Object getProperty(IClientConfigKey key);

	public Object getProperty(IClientConfigKey key, Object defaultVal);

	public boolean containsProperty(IClientConfigKey key);
	
	/**
	 * Returns the applicable virtual addresses ("vip") used by this client configuration.
	 * @return
	 */
	public String resolveDeploymentContextbasedVipAddresses();
	
	public int getPropertyAsInteger(IClientConfigKey key, int defaultValue);

    public String getPropertyAsString(IClientConfigKey key, String defaultValue);
    
    public boolean getPropertyAsBoolean(IClientConfigKey key, boolean defaultValue);

}
