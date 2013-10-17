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
package com.netflix.httpasyncclient;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.ConnectionPoolTimeoutException;

import com.google.common.collect.Lists;
import com.netflix.client.AsyncLoadBalancingClient;
import com.netflix.client.LoadBalancerContext;
import com.netflix.client.LoadBalancerErrorHandler;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

/**
 * The {@link LoadBalancerErrorHandler} to used with {@link AsyncLoadBalancingClient} if 
 * the underlying asynchronous client is {@link RibbonHttpAsyncClient}. 
 * 
 * @author awang
 *
 */
public class HttpAsyncClientLoadBalancerErrorHandler implements LoadBalancerErrorHandler<HttpRequest, HttpResponse> {

    @SuppressWarnings("unchecked")
    protected List<Class<? extends Throwable>> retriable = 
            Lists.<Class<? extends Throwable>>newArrayList(ConnectException.class, SocketTimeoutException.class, 
                    NoHttpResponseException.class, ConnectionPoolTimeoutException.class);
    
    @SuppressWarnings("unchecked")
    protected List<Class<? extends Throwable>> circuitRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class, SocketTimeoutException.class);
    
    /**
     * @return true if request is retriable and the Throwable has any of the following type of exception as a cause: 
     * {@link ConnectException}, {@link SocketTimeoutException}, {@link NoHttpResponseException}, {@link ConnectionPoolTimeoutException}
     * 
     */
    @Override
    public boolean isRetriableException(HttpRequest request, Throwable e,
            boolean sameServer) {
        if (request.isRetriable() && LoadBalancerContext.isPresentAsCause(e, retriable)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return true if the Throwable has one of the following exception type as a cause: 
     * {@link SocketException}, {@link SocketTimeoutException}
     */
    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        return LoadBalancerContext.isPresentAsCause(e, circuitRelated);
    }

    /**
     * @return true if the the response has status code 503 (throttle) 
     */
    @Override
    public boolean isCircuitTrippinErrorgResponse(HttpResponse response) {
        return response.getStatus() == 503;
    }
}
