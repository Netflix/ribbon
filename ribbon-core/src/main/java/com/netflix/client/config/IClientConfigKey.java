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
package com.netflix.client.config;

/**
 * Defines the key used in {@link IClientConfig}. See {@link CommonClientConfigKey}
 * for the commonly defined client configuration keys.
 * 
 * @author awang
 *
 */
public interface IClientConfigKey<T> {

    @SuppressWarnings("rawtypes")
    public static final class Keys extends CommonClientConfigKey {
        private Keys(String configKey) {
            super(configKey);
        }
    }
    
	/**
	 * @return string representation of the key used for hash purpose.
	 */
	public String key();
	
	/**
     * @return Data type for the key. For example, Integer.class.
	 */
	public Class<T> type();
}
