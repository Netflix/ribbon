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
package com.netflix.client;

import java.util.regex.Pattern;

import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;

/**
 * A "VipAddress" in Ribbon terminology is a logical name used for a target
 * server farm. This class helps interpret and resolve a "macro" and obtain a
 * finalized vipAddress.
 * 
 * Ribbon supports a comma separated set of logcial addresses for a Ribbon
 * Client. Typical/default implementation uses the list of servers obtained from
 * the first of the comma separated list and progresses down the list only when
 * the priorr vipAddress contains no servers.
 * 
 * e.g. vipAddress settings
 * 
 * <code>
 * ${foo}.bar:${port},${foobar}:80,localhost:8080
 * 
 * The above list will be resolved by this class as 
 * 
 * apple.bar:80,limebar:80,localhost:8080
 * 
 * provided that the Configuration library resolves the property foo=apple,port=80 and foobar=limebar
 * 
 * </code>
 * 
 * @author stonse
 * 
 */
public class SimpleVipAddressResolver implements VipAddressResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(.*?)\\}");

    @Override
    public String resolve(String vipAddressMacro, IClientConfig niwsClientConfig) {
        if (vipAddressMacro == null || vipAddressMacro.length() == 0) {
            return vipAddressMacro;
        }
        if (!VAR_PATTERN.matcher(vipAddressMacro).matches()) {
            return vipAddressMacro;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String address: vipAddressMacro.split(",")) {
            if (!first) {
                sb.append(",");
            }
            String interpolated = ConfigurationManager.getConfigInstance().getString(address);
            if (interpolated != null) {
                sb.append(interpolated);
            }
            first = false;
        }
        return sb.toString();
    }
}
