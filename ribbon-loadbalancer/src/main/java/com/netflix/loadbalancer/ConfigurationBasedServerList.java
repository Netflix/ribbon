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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Strings;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;

/**
 * Utility class that can load the List of Servers from a Configuration (i.e
 * properties available via Archaius). The property name be defined in this format:
 * 
 * <pre>{@code
<clientName>.<nameSpace>.listOfServers=<comma delimited hostname:port strings>
}</pre>
 * 
 * @author awang
 * 
 */
public class ConfigurationBasedServerList extends AbstractServerList<Server>  {

	public static final String PROP_NAME = "listOfServers";
	private String propertyName = DefaultClientConfigImpl.DEFAULT_PROPERTY_NAME_SPACE + "." + PROP_NAME;
	private DynamicStringProperty dynamicProp;
	private volatile List<Server> list = Collections.emptyList();
		
	@Override
	public List<Server> getInitialListOfServers() {
		return list;
	}

	@Override
	public List<Server> getUpdatedListOfServers() {
		return list;
	}

	@Override
	public void initWithNiwsConfig(IClientConfig clientConfig) {
		propertyName = clientConfig.getClientName() + "." + clientConfig.getNameSpace() + "." +  PROP_NAME;
		dynamicProp = DynamicPropertyFactory.getInstance().getStringProperty(propertyName, null);
		derive();
		dynamicProp.addCallback(new Runnable() {
			@Override
			public void run() {
				derive();
			}			
		});
	}
	
	private void derive() {
		String value = dynamicProp.get();
		if (Strings.isNullOrEmpty(value)) {
			list = Collections.emptyList();
		} else {
			List<Server> newList = new ArrayList<Server>();
			for (String s: value.split(",")) {
				newList.add(new Server(s.trim()));
			}
			list = newList;
		}
	}
}
