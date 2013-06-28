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
package com.netflix.niws.loadbalancer;


import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancerPing;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.Server;



/**
 * "Ping" Discovery Client
 * i.e. we dont do a real "ping". We just assume that the server is up if Discovery Client says so
 * @author stonse
 *
 */
public class NIWSDiscoveryPing extends AbstractLoadBalancerPing {
	        
		BaseLoadBalancer lb = null; 
		

		public NIWSDiscoveryPing() {
		}
		
		public BaseLoadBalancer getLb() {
			return lb;
		}

		/**
		 * Non IPing interface method - only set this if you care about the "newServers Feature"
		 * @param lb
		 */
		public void setLb(BaseLoadBalancer lb) {
			this.lb = lb;
		}

		public boolean isAlive(Server server) {
		    boolean isAlive = true;
		    if (server!=null && server instanceof DiscoveryEnabledServer){
	            DiscoveryEnabledServer dServer = (DiscoveryEnabledServer)server;	            
	            InstanceInfo instanceInfo = dServer.getInstanceInfo();
	            if (instanceInfo!=null){	                
	                InstanceStatus status = instanceInfo.getStatus();
	                if (status!=null){
	                    isAlive = status.equals(InstanceStatus.UP);
	                }
	            }
	        }
		    return isAlive;
		}

        @Override
        public void initWithNiwsConfig(
                IClientConfig clientConfig) {
        }
		
}
