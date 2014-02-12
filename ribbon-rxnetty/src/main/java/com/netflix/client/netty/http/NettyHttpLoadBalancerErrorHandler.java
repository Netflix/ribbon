/*
 *
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.client.netty.http;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;


import com.google.common.collect.Lists;
import com.netflix.client.DefaultLoadBalancerErrorHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.http.UnexpectedHttpResponseException;

public class NettyHttpLoadBalancerErrorHandler extends DefaultLoadBalancerErrorHandler {

    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> retriable = 
            Lists.<Class<? extends Throwable>>newArrayList(ConnectException.class, SocketTimeoutException.class, 
                    io.netty.handler.timeout.ReadTimeoutException.class, io.netty.channel.ConnectTimeoutException.class);
    
    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> circuitRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class, SocketTimeoutException.class, 
                    io.netty.handler.timeout.ReadTimeoutException.class, io.netty.channel.ConnectTimeoutException.class);
    
    public NettyHttpLoadBalancerErrorHandler() {
        super();
    }

    public NettyHttpLoadBalancerErrorHandler(IClientConfig clientConfig) {
        super(clientConfig);
    }
    
    public NettyHttpLoadBalancerErrorHandler(int retrySameServer, int retryNextServer, boolean retryEnabled) {
        super(retrySameServer, retryNextServer, retryEnabled);
    }
    
    /**
     * @return true if the Throwable has one of the following exception type as a cause: 
     * {@link SocketException}, {@link SocketTimeoutException}
     */
    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        if (e instanceof UnexpectedHttpResponseException) {
            HttpResponse response = ((UnexpectedHttpResponseException) e).getResponse();
            return isCircuitTrippinResponse(response);
        }
        return super.isCircuitTrippingException(e);
    }

    /**
     * @return true if the the response has status code 503 (throttle) 
     */
    @Override
    public boolean isCircuitTrippinResponse(Object response) {
        if (!(response instanceof HttpResponse)) {
            return false;
        }
        return ((HttpResponse) response).getStatus() == 503;
    }

    @Override
    protected List<Class<? extends Throwable>> getRetriableExceptions() {
        return retriable;
    }

    @Override
    protected List<Class<? extends Throwable>> getCircuitRelatedExceptions() {
        return circuitRelated;
    }
}
