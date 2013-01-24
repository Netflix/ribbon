package com.netflix.loadbalancer;

import java.util.List;

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicListProperty;

public class ConfigurationBasedServerList extends AbstractNIWSServerList<Server>  {

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
