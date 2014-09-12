
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
package com.netflix.ribbon.transport.netty.udp;

import com.google.common.collect.Lists;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.ribbon.transport.netty.MyUDPClient;
import com.netflix.ribbon.transport.netty.RibbonTransport;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.Server;
import io.netty.channel.socket.DatagramPacket;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.protocol.udp.server.UdpServer;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func1;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by awang on 8/5/14.
 */
public class UdpClientTest {

    public int choosePort() throws SocketException {
        DatagramSocket serverSocket = new DatagramSocket();
        int port = serverSocket.getLocalPort();
        serverSocket.close();
        return port;
    }

    @Test
    public void testUdpClientWithoutTimeout() throws Exception {
        int port = choosePort();
        UdpServer<DatagramPacket, DatagramPacket> server = new HelloUdpServer(port, 0).createServer();
        server.start();
        BaseLoadBalancer lb = new BaseLoadBalancer();
        lb.setServersList(Lists.newArrayList(new Server("localhost", port)));
        RxClient<DatagramPacket, DatagramPacket> client = RibbonTransport.newUdpClient(lb,
                DefaultClientConfigImpl.getClientConfigWithDefaultValues());
        try {
            String response = client.connect().flatMap(new Func1<ObservableConnection<DatagramPacket, DatagramPacket>,
                    Observable<DatagramPacket>>() {
                @Override
                public Observable<DatagramPacket> call(ObservableConnection<DatagramPacket, DatagramPacket> connection) {
                    connection.writeStringAndFlush("Is there anybody out there?");
                    return connection.getInput();
                }
            }).take(1)
                    .map(new Func1<DatagramPacket, String>() {
                        @Override
                        public String call(DatagramPacket datagramPacket) {
                            return datagramPacket.content().toString(Charset.defaultCharset());
                        }
                    })
                    .toBlocking()
                    .first();
            assertEquals(HelloUdpServer.WELCOME_MSG, response);
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void testUdpClientTimeout() throws Exception {
        int port = choosePort();
        UdpServer<DatagramPacket, DatagramPacket> server = new HelloUdpServer(port, 5000).createServer();
        server.start();
        BaseLoadBalancer lb = new BaseLoadBalancer();
        Server myServer = new Server("localhost", port);
        lb.setServersList(Lists.newArrayList(myServer));
        MyUDPClient client = new MyUDPClient(lb, DefaultClientConfigImpl.getClientConfigWithDefaultValues());
        try {
            String response = client.submit("Is there anybody out there?")
                    .map(new Func1<DatagramPacket, String>() {
                        @Override
                        public String call(DatagramPacket datagramPacket) {
                            return datagramPacket.content().toString(Charset.defaultCharset());
                        }
                    })
                    .toBlocking()
                    .first();
            fail("Exception expected");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof TimeoutException);
            assertEquals(1, client.getLoadBalancerContext().getServerStats(myServer).getSuccessiveConnectionFailureCount());
        }
        finally {
            server.shutdown();
        }
    }

}
