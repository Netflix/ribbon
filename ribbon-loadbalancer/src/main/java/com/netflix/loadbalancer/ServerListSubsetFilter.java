/**
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.loadbalancer;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.config.Property;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A server list filter that limits the number of the servers used by the load balancer to be the subset of all servers.
 * This is useful if the server farm is large (e.g., in the hundreds) and making use of every one of them
 * and keeping the connections in http client's connection pool is unnecessary. It also has the capability of eviction 
 * of relatively unhealthy servers by comparing the total network failures and concurrent connections. 
 *  
 * @author awang
 *
 * @param <T>
 */
public class ServerListSubsetFilter<T extends Server> extends ZoneAffinityServerListFilter<T> implements IClientConfigAware, Comparator<T>{

    private Random random = new Random();
    private volatile Set<T> currentSubset = Sets.newHashSet(); 
    private Property<Integer> sizeProp;
    private Property<Float> eliminationPercent;
    private Property<Integer> eliminationFailureCountThreshold;
    private Property<Integer> eliminationConnectionCountThreshold;

    private static final IClientConfigKey<Integer> SIZE = new CommonClientConfigKey<Integer>("ServerListSubsetFilter.size", 20) {};
    private static final IClientConfigKey<Float> FORCE_ELIMINATE_PERCENT = new CommonClientConfigKey<Float>("ServerListSubsetFilter.forceEliminatePercent", 0.1f) {};
    private static final IClientConfigKey<Integer> ELIMINATION_FAILURE_THRESHOLD = new CommonClientConfigKey<Integer>("ServerListSubsetFilter.eliminationFailureThresold", 0) {};
    private static final IClientConfigKey<Integer> ELIMINATION_CONNECTION_THRESHOLD = new CommonClientConfigKey<Integer>("ServerListSubsetFilter.eliminationConnectionThresold", 0) {};

    /**
     * @deprecated ServerListSubsetFilter should only be created with an IClientConfig.  See {@link ServerListSubsetFilter#ServerListSubsetFilter(IClientConfig)}
     */
    @Deprecated
    public ServerListSubsetFilter() {
        sizeProp = Property.of(SIZE.defaultValue());
        eliminationPercent = Property.of(FORCE_ELIMINATE_PERCENT.defaultValue());
        eliminationFailureCountThreshold = Property.of(ELIMINATION_FAILURE_THRESHOLD.defaultValue());
        eliminationConnectionCountThreshold = Property.of(ELIMINATION_CONNECTION_THRESHOLD.defaultValue());
    }

    public ServerListSubsetFilter(IClientConfig clientConfig) {
        super(clientConfig);

        initWithNiwsConfig(clientConfig);
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        sizeProp = clientConfig.getDynamicProperty(SIZE);
        eliminationPercent = clientConfig.getDynamicProperty(FORCE_ELIMINATE_PERCENT);
        eliminationFailureCountThreshold = clientConfig.getDynamicProperty(ELIMINATION_FAILURE_THRESHOLD);
        eliminationConnectionCountThreshold = clientConfig.getDynamicProperty(ELIMINATION_CONNECTION_THRESHOLD);
    }

    /**
     * Given all the servers, keep only a stable subset of servers to use. This method
     * keeps the current list of subset in use and keep returning the same list, with exceptions
     * to relatively unhealthy servers, which are defined as the following:
     * <p>
     * <ul>
     * <li>Servers with their concurrent connection count exceeding the client configuration for 
     *  {@code <clientName>.<nameSpace>.ServerListSubsetFilter.eliminationConnectionThresold} (default is 0)
     * <li>Servers with their failure count exceeding the client configuration for 
     *  {@code <clientName>.<nameSpace>.ServerListSubsetFilter.eliminationFailureThresold}  (default is 0)
     *  <li>If the servers evicted above is less than the forced eviction percentage as defined by client configuration
     *   {@code <clientName>.<nameSpace>.ServerListSubsetFilter.forceEliminatePercent} (default is 10%, or 0.1), the
     *   remaining servers will be sorted by their health status and servers will worst health status will be
     *   forced evicted.
     * </ul>
     * <p>
     * After the elimination, new servers will be randomly chosen from all servers pool to keep the
     * number of the subset unchanged. 
     * 
     */
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
                if (stats.getActiveRequestsCount() > eliminationConnectionCountThreshold.getOrDefault()
                        || stats.getFailureCount() > eliminationFailureCountThreshold.getOrDefault()) {
                    newSubSet.remove(server);
                    // also remove from the general pool to avoid selecting them again
                    candidates.remove(server);
                }
            }
        }
        int targetedListSize = sizeProp.getOrDefault();
        int numEliminated = currentSubset.size() - newSubSet.size();
        int minElimination = (int) (targetedListSize * eliminationPercent.getOrDefault());
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
     * unhealthy servers before healthy servers. The servers are first sorted by
     * failures count, and then concurrent connection count.
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
