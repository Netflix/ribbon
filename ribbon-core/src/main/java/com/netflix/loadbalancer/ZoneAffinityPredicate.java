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

import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext.ContextKey;
import com.netflix.loadbalancer.AbstractServerPredicate;
import com.netflix.loadbalancer.PredicateKey;
import com.netflix.loadbalancer.Server;

/**
 * A predicate the filters out servers that are not in the same zone as the client's current
 * zone. The current zone is determined from the call
 * 
 * <pre>{@code
 * ConfigurationManager.getDeploymentContext().getValue(ContextKey.zone);
 * }</pre>
 * 
 * @author awang
 *
 */
public class ZoneAffinityPredicate extends AbstractServerPredicate {

    private final String zone = ConfigurationManager.getDeploymentContext().getValue(ContextKey.zone);
    
    public ZoneAffinityPredicate() {        
    }

    @Override
    public boolean apply(PredicateKey input) {
        Server s = input.getServer();
        String az = s.getZone();
        if (az != null && zone != null && az.toLowerCase().equals(zone.toLowerCase())) {
            return true;
        } else {
            return false;
        }
    }
}
