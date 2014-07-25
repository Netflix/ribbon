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

/**
 * The input object of predicates of class {@link AbstractServerPredicate}.
 * It includes Server and an Object as load balancer key used in {@link IRule#choose(Object)},
 * which might be null. 
 * 
 * @author awang
 *
 */
public class PredicateKey {
    private Object loadBalancerKey;
    private Server server;
    
    public PredicateKey(Object loadBalancerKey, Server server) {
        this.loadBalancerKey = loadBalancerKey;
        this.server = server;
    }

    public PredicateKey(Server server) {
        this(null, server);
    }
    
    public final Object getLoadBalancerKey() {
        return loadBalancerKey;
    }
    
    public final Server getServer() {
        return server;
    }        
}
