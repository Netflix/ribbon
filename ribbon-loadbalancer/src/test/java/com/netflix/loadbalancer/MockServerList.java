package com.netflix.loadbalancer;

import java.util.ArrayList;
import java.util.List;

import com.netflix.client.config.IClientConfig;

public class MockServerList extends AbstractServerList<Server>  {
    
    private List<Server> serverList = new ArrayList<>();
        
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
