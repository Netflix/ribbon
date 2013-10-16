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
package com.netflix.client.http;

import java.nio.ByteBuffer;

import com.netflix.client.AsyncClient;
import com.netflix.client.AsyncLoadBalancingClient;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.serialization.ContentTypeBasedSerializerKey;

/**
 * An asynchronous HTTP client that is capable of load balancing from an {@link ILoadBalancer}. 
 * 
 * @author awang
 * @see AsyncLoadBalancingClient
 *
 * @param <T> Type of storage used for delivering partial content, for example, {@link ByteBuffer}
 */
public class AsyncLoadBalancingHttpClient<T> 
        extends AsyncLoadBalancingClient<HttpRequest, HttpResponse, T, ContentTypeBasedSerializerKey>
        implements AsyncHttpClient<T> {
    
    public AsyncLoadBalancingHttpClient(AsyncClient<HttpRequest, HttpResponse, T, ContentTypeBasedSerializerKey> client, IClientConfig config) {
        super(client, config);
    }    
}
