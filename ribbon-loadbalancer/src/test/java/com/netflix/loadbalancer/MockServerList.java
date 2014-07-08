package com.netflix.loadbalancer;

import java.util.List;

import com.google.common.collect.Lists;
import com.netflix.client.config.IClientConfig;

public class MockServerList extends AbstractServerList<Server>  {
    
    private List<Server> serverList = Lists.newArrayList();
        
    public void setServerList(List<Server> serverList) {
        this.serverList = serverList;
    }
    
    @Override
    public List<Server> getInitialListOfServers() {
        return serverList;
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        return serverList;
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
    }
}
