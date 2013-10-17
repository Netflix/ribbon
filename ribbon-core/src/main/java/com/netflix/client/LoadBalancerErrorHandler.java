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

import java.net.ConnectException;

/**
 * A handler that determines if an exception is retriable for load balancer,
 * and if an exception or error response should be treated as circuit related failures
 * so that the load balancer can avoid such server.
 *  
 * @author awang
 *
 * @param <T> Type of request
 * @param <S> Type fo response
 */
public interface LoadBalancerErrorHandler<T extends ClientRequest, S extends IResponse> {

    /**
     * Test if an exception is retriable for the load balancer
     * 
     * @param request Request that causes such exception
     * @param sameServer if true, the method is trying to determine if retry can be 
     *        done on the same server. Otherwise, it is testing whether retry can be
     *        done on a different server
     */
    public boolean isRetriableException(T request, Throwable e, boolean sameServer);

    /**
     * Test if an exception should be treated as circuit failure. For example, 
     * a {@link ConnectException} is a circuit failure. 
     */
    public boolean isCircuitTrippingException(Throwable e);
    
    /**
     * Test if an error response should be treated as circuit failure. For example,
     * HTTP throttling (503 status code) might be considered circuit failure where 
     * load balancer should avoid sending next request to such server.   
     */
    public boolean isCircuitTrippinErrorgResponse(S response);
}
