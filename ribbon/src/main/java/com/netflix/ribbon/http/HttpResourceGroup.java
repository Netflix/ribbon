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

import com.netflix.client.config.ClientConfigFactory;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.ResourceGroup;
import com.netflix.ribbon.RibbonTransportFactory;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.reactivex.netty.protocol.http.client.HttpClient;

public class HttpResourceGroup extends ResourceGroup<HttpRequestTemplate<?>> {
    private final HttpClient<ByteBuf, ByteBuf> client;
    private final HttpHeaders headers;

    public static class Builder extends GroupBuilder<HttpResourceGroup> {
        private ClientOptions clientOptions;
        private HttpHeaders httpHeaders = new DefaultHttpHeaders();
        private ClientConfigFactory clientConfigFactory;
        private RibbonTransportFactory transportFactory;
        private String name;

        private Builder(String name, ClientConfigFactory configFactory, RibbonTransportFactory transportFactory) {
            this.name = name;
            this.clientConfigFactory = configFactory;
            this.transportFactory = transportFactory;
        }

        public static Builder newBuilder(String groupName, ClientConfigFactory configFactory, RibbonTransportFactory transportFactory) {
            return new Builder(groupName, configFactory, transportFactory);
        }

        @Override
        public Builder withClientOptions(ClientOptions options) {
            this.clientOptions = options;
            return this;
        }

        public Builder withHeader(String name, String value) {
            httpHeaders.add(name, value);
            return this;
        }

        @Override
        public HttpResourceGroup build() {
            return new HttpResourceGroup(name, clientOptions, clientConfigFactory, transportFactory, httpHeaders);
        }
    }

    protected HttpResourceGroup(String groupName) {
        super(groupName, ClientOptions.create(), ClientConfigFactory.DEFAULT, RibbonTransportFactory.DEFAULT);
        client = transportFactory.newHttpClient(getClientConfig());
        headers = HttpHeaders.EMPTY_HEADERS;
    }

    protected HttpResourceGroup(String groupName, ClientOptions options, ClientConfigFactory configFactory, RibbonTransportFactory transportFactory, HttpHeaders headers) {
        super(groupName, options, configFactory, transportFactory);
        client = transportFactory.newHttpClient(getClientConfig());
        this.headers = headers;
    }

    @Override
    public <T> HttpRequestTemplate.Builder newTemplateBuilder(String name, Class<? extends T> classType) {
        return HttpRequestTemplate.Builder.newBuilder(name, this, classType);
    }

    public HttpRequestTemplate.Builder<ByteBuf> newTemplateBuilder(String name) {
        return HttpRequestTemplate.Builder.newBuilder(name, this, ByteBuf.class);
    }

    public final HttpHeaders getHeaders() {
        return headers;
    }
    
    public final HttpClient<ByteBuf, ByteBuf> getClient() {
        return client;
    }
}
