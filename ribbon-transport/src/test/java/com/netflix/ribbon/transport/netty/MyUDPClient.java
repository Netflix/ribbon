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
package com.netflix.ribbon.transport.netty;

import com.google.common.collect.Lists;
import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.transport.netty.udp.LoadBalancingUdpClient;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import com.netflix.loadbalancer.reactive.ServerOperation;
import com.netflix.loadbalancer.Server;

import io.netty.channel.socket.DatagramPacket;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import rx.Observable;
import rx.functions.Func1;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by awang on 8/5/14.
 */
public class MyUDPClient extends LoadBalancingUdpClient<DatagramPacket, DatagramPacket> {

    private static final class MyRetryHandler extends DefaultLoadBalancerRetryHandler {
        @SuppressWarnings("unchecked")
        private List<Class<? extends Throwable>> timeoutExceptions =
                Lists.<Class<? extends Throwable>>newArrayList(TimeoutException.class);

        private MyRetryHandler(IClientConfig clientConfig) {
            super(clientConfig);
        }

        @Override
        protected List<Class<? extends Throwable>> getCircuitRelatedExceptions() {
            return timeoutExceptions;
        }
    }

    public MyUDPClient(IClientConfig config, PipelineConfigurator<DatagramPacket, DatagramPacket> pipelineConfigurator) {
        super(config, new MyRetryHandler(config), pipelineConfigurator);
    }

    public MyUDPClient(ILoadBalancer lb, IClientConfig config) {
        super(lb, config, new MyRetryHandler(config), null);
    }

    public Observable<DatagramPacket> submit(final String content) {
        return LoadBalancerCommand.<DatagramPacket>builder()
                .withLoadBalancerContext(lbContext)
                .build()
                .submit(new ServerOperation<DatagramPacket>() {
                    @Override
                    public Observable<DatagramPacket> call(Server server) {
                        RxClient<DatagramPacket, DatagramPacket> rxClient = getOrCreateRxClient(server);
                        return rxClient.connect().flatMap(new Func1<ObservableConnection<DatagramPacket, DatagramPacket>, Observable<? extends DatagramPacket>>() {
                            @Override
                            public Observable<? extends DatagramPacket> call(ObservableConnection<DatagramPacket, DatagramPacket> connection) {
                                connection.writeStringAndFlush(content);
                                return connection.getInput().timeout(10, TimeUnit.MILLISECONDS).take(1);
                            }
                        });
                    }
                });
    }
}
