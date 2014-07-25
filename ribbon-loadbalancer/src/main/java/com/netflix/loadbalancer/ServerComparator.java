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

import java.io.Serializable;
import java.util.Comparator;

/**
 * Class to help establishing equality for Hash/Key operations.
 * 
 * @author stonse
 * 
 */
public class ServerComparator implements Comparator<Server>, Serializable {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public int compare(Server s1, Server s2) {
        return s1.getHostPort().compareTo(s2.getId());
    }
}
