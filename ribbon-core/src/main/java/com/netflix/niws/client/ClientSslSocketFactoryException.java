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
package com.netflix.niws.client;

/**
 * Reports problems detected by the ClientSslSocketFactory class.
 *
 * @author pstout@netflix.com (Peter D. Stout)
 */
public class ClientSslSocketFactoryException extends Exception {
    /** Serial version identifier for this class. */
    private static final long serialVersionUID = 1L;

    /** Constructs a new instance with the specified message and cause. */
    public ClientSslSocketFactoryException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
