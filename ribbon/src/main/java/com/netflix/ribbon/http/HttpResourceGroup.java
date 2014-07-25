/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.reactivex.netty.protocol.http.client.HttpClient;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.RibbonTransport;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.ResourceGroup;

public class HttpResourceGroup extends ResourceGroup<HttpRequestTemplate<?>> {
    private final HttpClient<ByteBuf, ByteBuf> client;
    private final HttpHeaders headers;
    
    public HttpResourceGroup(String groupName) {
        this(groupName, null);
    }
    
    public HttpResourceGroup(String groupName, ClientOptions options) {
        super(groupName, options);
        client = RibbonTransport.newHttpClient(getClientConfig());
        headers = new DefaultHttpHeaders();
    }

    @Override
    protected IClientConfig loadDefaultConfig(String groupName) {
        return IClientConfig.Builder.newBuilder(groupName).build();
    }
    
    public HttpResourceGroup withCommonHeader(String name, String value) {
        headers.add(name, value);
        return this;
    }

    @Override
    public <T> HttpRequestTemplate<T> newRequestTemplate(String name,
            Class<? extends T> classType) {
        return new HttpRequestTemplate<T>(name, this, classType);
    }
    
    public HttpRequestTemplate<ByteBuf> newRequestTemplate(String name) {
        return newRequestTemplate(name, ByteBuf.class);
    }
    
    HttpHeaders getHeaders() {
        return headers;
    }
    
    public HttpClient<ByteBuf, ByteBuf> getClient() {
        return client;
    }
}
