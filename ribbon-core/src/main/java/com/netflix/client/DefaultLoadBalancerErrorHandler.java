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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * A default {@link LoadBalancerErrorHandler}. The implementation is limited to
 * known exceptions in java.net. Specific client implementation should provide its own
 * {@link LoadBalancerErrorHandler}
 * 
 * @author awang
 *
 * @param <T> Type of request
 * @param <S> Type of response
 */
public class DefaultLoadBalancerErrorHandler<T extends ClientRequest, S extends IResponse> 
        implements LoadBalancerErrorHandler<T, S> {

    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> retriable = 
            Lists.<Class<? extends Throwable>>newArrayList(ConnectException.class, SocketTimeoutException.class);
    
    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> circuitRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class, SocketTimeoutException.class);

    
    /**
     * @return false if request is not retriable. otherwise, return true if
     *            {@link ConnectException} or {@link SocketTimeoutException} 
     *            is a cause in the Throwable. 
     */
    @Override
    public boolean isRetriableException(T request, Throwable e,
            boolean sameServer) {
        if (!request.isRetriable()) {
            return false;
        } else {
            return LoadBalancerContext.isPresentAsCause(e, retriable);
        }
    }

    /**
     * @return true if {@link SocketException} or {@link SocketTimeoutException} is a cause in the Throwable.
     */
    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        return LoadBalancerContext.isPresentAsCause(e, circuitRelated);        
    }

    /**
     * always return false
     */
    @Override
    public boolean isCircuitTrippinErrorgResponse(S response) {
        return false;
    }
}
