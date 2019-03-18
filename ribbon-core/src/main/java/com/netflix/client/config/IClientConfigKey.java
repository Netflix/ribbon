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
	final class Keys extends CommonClientConfigKey {
        private Keys(String configKey) {
            super(configKey);
        }
    }
    
	/**
	 * @return string representation of the key used for hash purpose.
	 */
	String key();
	
	/**
     * @return Data type for the key. For example, Integer.class.
	 */
	Class<T> type();

	default T defaultValue() { return null; }

	default IClientConfigKey<T> format(Object ... args) {
		return create(String.format(key(), args), type(), defaultValue());
	}

	default IClientConfigKey<T> create(String key, Class<T> type, T defaultValue) {
		return new IClientConfigKey<T>() {

			@Override
			public int hashCode() {
				return key().hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (obj instanceof IClientConfigKey) {
					return key().equals(((IClientConfigKey)obj).key());
				}
				return false;
			}

			@Override
			public String toString() {
				return key();
			}

			@Override
			public String key() {
				return key;
			}

			@Override
			public Class<T> type() {
				return type;
			}

			@Override
			public T defaultValue() { return defaultValue; }
		};
	}
}
