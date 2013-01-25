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

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicListProperty;

public class ConfigurationBasedServerList extends AbstractServerList<Server>  {

	public static final String PROP_NAME = "listOfServers";
	private String propertyName = DefaultClientConfigImpl.DEFAULT_PROPERTY_NAME_SPACE + "." + PROP_NAME;
	private DynamicListProperty<Server> dynamicProp;
		
	@Override
	public List<Server> getInitialListOfServers() {
		return dynamicProp.get();
	}

	@Override
	public List<Server> getUpdatedListOfServers() {
		return dynamicProp.get();
	}

	@Override
	public void initWithNiwsConfig(IClientConfig clientConfig) {
		propertyName = clientConfig.getClientName() + "." + clientConfig.getNameSpace() + "." +  PROP_NAME;
		dynamicProp = new DynamicListProperty<Server>(propertyName, "") {
			@Override
			protected Server from(String value) {
				return new Server(value);
			}			
		};
	}
}
