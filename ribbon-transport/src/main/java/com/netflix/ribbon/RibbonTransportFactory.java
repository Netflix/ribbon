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
package com.netflix.ribbon;

import com.netflix.client.config.ClientConfigFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.transport.netty.RibbonTransport;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramPacket;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.protocol.http.client.HttpClient;

import javax.inject.Inject;

/**
 * A dependency injection friendly Ribbon transport client factory that can create clients based on IClientConfig or
 * a name which is used to construct the necessary IClientConfig.
 *
 * Created by awang on 7/18/14.
 */
public abstract class RibbonTransportFactory {
    protected final ClientConfigFactory clientConfigFactory;

    public static class DefaultRibbonTransportFactory extends RibbonTransportFactory {
        @Inject
        public DefaultRibbonTransportFactory(ClientConfigFactory clientConfigFactory) {
            super(clientConfigFactory);
        }
    }

    protected RibbonTransportFactory(ClientConfigFactory clientConfigFactory) {
        this.clientConfigFactory = clientConfigFactory;
    }

    public static final RibbonTransportFactory DEFAULT = new DefaultRibbonTransportFactory(ClientConfigFactory.DEFAULT);

    public HttpClient<ByteBuf, ByteBuf> newHttpClient(IClientConfig config) {
        return RibbonTransport.newHttpClient(config);
    }

    public RxClient<ByteBuf, ByteBuf> newTcpClient(IClientConfig config) {
        return RibbonTransport.newTcpClient(config);
    }

    public RxClient<DatagramPacket, DatagramPacket> newUdpClient(IClientConfig config) {
        return RibbonTransport.newUdpClient(config);
    }

    public final HttpClient<ByteBuf, ByteBuf> newHttpClient(String name) {
        IClientConfig config = clientConfigFactory.newConfig();
        config.loadProperties(name);
        return newHttpClient(config);
    }

    public final RxClient<ByteBuf, ByteBuf> newTcpClient(String name) {
        IClientConfig config = clientConfigFactory.newConfig();
        config.loadProperties(name);
        return newTcpClient(config);
    }

    public RxClient<DatagramPacket, DatagramPacket> newUdpClient(String name) {
        IClientConfig config = clientConfigFactory.newConfig();
        config.loadProperties(name);
        return newUdpClient(config);
    }
}
