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
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.loadbalancer.Server;

/**
 * Servers that were obtained via Discovery and hence contain
 * meta data in the form of InstanceInfo
 * @author stonse
 *
 */
@edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "EQ_DOESNT_OVERRIDE_EQUALS")
public class DiscoveryEnabledServer extends Server{

    private final InstanceInfo instanceInfo;
    private final MetaInfo serviceInfo;

    public DiscoveryEnabledServer(final InstanceInfo instanceInfo, boolean useSecurePort) {
        this(instanceInfo, useSecurePort, false);
    }

    public DiscoveryEnabledServer(final InstanceInfo instanceInfo, boolean useSecurePort, boolean useIpAddr) {
        super(useIpAddr ? instanceInfo.getIPAddr() : instanceInfo.getHostName(), instanceInfo.getPort());
    	if(useSecurePort && instanceInfo.isPortEnabled(PortType.SECURE))
    		super.setPort(instanceInfo.getSecurePort());
        this.instanceInfo = instanceInfo;
        this.serviceInfo = new MetaInfo() {
            @Override
            public String getAppName() {
                return instanceInfo.getAppName();
            }

            @Override
            public String getServerGroup() {
                return instanceInfo.getASGName();
            }

            @Override
            public String getServiceIdForDiscovery() {
                return instanceInfo.getVIPAddress();
            }

            @Override
            public String getInstanceId() {
                return instanceInfo.getId();
            }
        };
    }
    
    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }

    @Override
    public MetaInfo getMetaInfo() {
        return serviceInfo;
    }
}
