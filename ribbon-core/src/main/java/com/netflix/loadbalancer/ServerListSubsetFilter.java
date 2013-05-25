package com.netflix.loadbalancer;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicFloatProperty;
import com.netflix.config.DynamicIntProperty;

public class ServerListSubsetFilter<T extends Server> extends ZoneAffinityServerListFilter<T> implements IClientConfigAware, Comparator<T>{

    private Random random = new Random();
    private volatile Set<T> currentSubset = Sets.newHashSet(); 
    private DynamicIntProperty sizeProp = new DynamicIntProperty(DefaultClientConfigImpl.DEFAULT_PROPERTY_NAME_SPACE + ".ServerListSubsetFilter.size", 20);
    private DynamicFloatProperty eliminationPercent = 
            new DynamicFloatProperty(DefaultClientConfigImpl.DEFAULT_PROPERTY_NAME_SPACE + ".ServerListSubsetFilter.forceEliminatePercent", 0.1f);
    private DynamicIntProperty eliminationFailureCountThreshold = 
            new DynamicIntProperty(DefaultClientConfigImpl.DEFAULT_PROPERTY_NAME_SPACE + ".ServerListSubsetFilter.eliminationFailureThresold", 0);
    private DynamicIntProperty eliminationConnectionCountThreshold = 
            new DynamicIntProperty(DefaultClientConfigImpl.DEFAULT_PROPERTY_NAME_SPACE + ".ServerListSubsetFilter.eliminationConnectionThresold", 0);
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        super.initWithNiwsConfig(clientConfig);
        sizeProp = new DynamicIntProperty(clientConfig.getClientName() + "." + clientConfig.getNameSpace() + ".ServerListSubsetFilter.size", 20);
        eliminationPercent = 
                new DynamicFloatProperty(clientConfig.getClientName() + "." + clientConfig.getNameSpace() + ".ServerListSubsetFilter.forceEliminatePercent", 0.1f);
        eliminationFailureCountThreshold = new DynamicIntProperty( clientConfig.getClientName()  + "." + clientConfig.getNameSpace()
                + ".ServerListSubsetFilter.eliminationFailureThresold", 0);
        eliminationConnectionCountThreshold = new DynamicIntProperty(clientConfig.getClientName() + "." + clientConfig.getNameSpace()
                + ".ServerListSubsetFilter.eliminationConnectionThresold", 0);
    }
        
    @Override
    public List<T> getFilteredListOfServers(List<T> servers) {
        List<T> zoneAffinityFiltered = super.getFilteredListOfServers(servers);
        Set<T> candidates = Sets.newHashSet(zoneAffinityFiltered);
        Set<T> newSubSet = Sets.newHashSet(currentSubset);
        LoadBalancerStats lbStats = getLoadBalancerStats();
        for (T server: currentSubset) {
            // this server is either down or out of service
            if (!candidates.contains(server)) {
                newSubSet.remove(server);
            } else {
                ServerStats stats = lbStats.getSingleServerStat(server);
                // remove the servers that do not meet health criteria
                if (stats.getActiveRequestsCount() > eliminationConnectionCountThreshold.get()
                        || stats.getFailureCount() > eliminationFailureCountThreshold.get()) {
                    newSubSet.remove(server);
                    // also remove from the general pool to avoid selecting them again
                    candidates.remove(server);
                }
            }
        }
        int targetedListSize = sizeProp.get();
        int numEliminated = currentSubset.size() - newSubSet.size();
        int minElimination = (int) (targetedListSize * eliminationPercent.get());
        int numToForceEliminate = 0;
        if (targetedListSize < newSubSet.size()) {
            // size is shrinking
            numToForceEliminate = newSubSet.size() - targetedListSize;
        } else if (minElimination > numEliminated) {
            numToForceEliminate = minElimination - numEliminated; 
        }
        
        if (numToForceEliminate > newSubSet.size()) {
            numToForceEliminate = newSubSet.size();
        }

        if (numToForceEliminate > 0) {
            List<T> sortedSubSet = Lists.newArrayList(newSubSet);           
            Collections.sort(sortedSubSet, this);
            List<T> forceEliminated = sortedSubSet.subList(0, numToForceEliminate);
            newSubSet.removeAll(forceEliminated);
            candidates.removeAll(forceEliminated);
        }
        
        // after forced elimination or elimination of unhealthy instances,
        // the size of the set may be less than the targeted size,
        // then we just randomly add servers from the big pool
        if (newSubSet.size() < targetedListSize) {
            int numToChoose = targetedListSize - newSubSet.size();
            candidates.removeAll(newSubSet);
            if (numToChoose > candidates.size()) {
                // Not enough healthy instances to choose, fallback to use the
                // total server pool
                candidates = Sets.newHashSet(zoneAffinityFiltered);
                candidates.removeAll(newSubSet);
            }
            List<T> chosen = randomChoose(Lists.newArrayList(candidates), numToChoose);
            for (T server: chosen) {
                newSubSet.add(server);
            }
        }
        currentSubset = newSubSet;       
        return Lists.newArrayList(newSubSet);            
    }

    /**
     * Randomly shuffle the beginning portion of server list (according to the number passed into the method) 
     * and return them.
     *  
     * @param servers
     * @param toChoose
     * @return
     */
    private List<T> randomChoose(List<T> servers, int toChoose) {
        int size = servers.size();
        if (toChoose >= size || toChoose < 0) {
            return servers;
        } 
        for (int i = 0; i < toChoose; i++) {
            int index = random.nextInt(size);
            T tmp = servers.get(index);
            servers.set(index, servers.get(i));
            servers.set(i, tmp);
        }
        return servers.subList(0, toChoose);        
    }

    /**
     * Function to sort the list by server health condition, with
     * unhealthy servers before healthy servers 
     */
    @Override
    public int compare(T server1, T server2) {
        LoadBalancerStats lbStats = getLoadBalancerStats();
        ServerStats stats1 = lbStats.getSingleServerStat(server1);
        ServerStats stats2 = lbStats.getSingleServerStat(server2);
        int failuresDiff = (int) (stats2.getFailureCount() - stats1.getFailureCount());
        if (failuresDiff != 0) {
            return failuresDiff;
        } else {
            return (stats2.getActiveRequestsCount() - stats1.getActiveRequestsCount());
        }
    }
}
