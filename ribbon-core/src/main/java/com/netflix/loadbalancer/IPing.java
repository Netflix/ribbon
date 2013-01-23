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
 * Interface that defines how we "ping" a server to check if its alive
 * @author stonse
 *
 */
public interface IPing {
    /*
     *
     * Send one ping only.
     *
     * Well, send what you need to determine whether or not the
     * server is still alive.  Should be interruptible.  Will
     * run in a separate thread.
	 *
	 * Warning:  multiple threads will execute this method 
	 * simultaneously.  Must be MT-safe.
     */

    public boolean isAlive(Server server);
}
