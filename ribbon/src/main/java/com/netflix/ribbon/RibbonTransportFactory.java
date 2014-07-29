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

import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.RibbonTransport;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramPacket;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.protocol.http.client.HttpClient;

/**
 * Created by awang on 7/18/14.
 */
public abstract class RibbonTransportFactory {
    public static class DefaultRibbonTransportFactory extends RibbonTransportFactory {
    }

    public static final RibbonTransportFactory DEFAULT = new DefaultRibbonTransportFactory();

    public HttpClient<ByteBuf, ByteBuf> newHttpClient(IClientConfig config) {
        return RibbonTransport.newHttpClient(config);
    }

    public RxClient<ByteBuf, ByteBuf> newTcpClient(IClientConfig config) {
        return RibbonTransport.newTcpClient(config);
    }

    public RxClient<DatagramPacket, DatagramPacket> newUdpClient(IClientConfig config) {
        return RibbonTransport.newUdpClient(config);
    }
}
