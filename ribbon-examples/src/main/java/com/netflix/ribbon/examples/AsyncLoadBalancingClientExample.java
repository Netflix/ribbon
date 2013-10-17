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
package com.netflix.ribbon.examples;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import com.netflix.client.AsyncBackupRequestsExecutor.ExecutionResult;
import com.netflix.client.AsyncLoadBalancingClient;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.AsyncLoadBalancingHttpClient;
import com.netflix.client.http.BufferedHttpResponseCallback;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.Server;

/**
 * An example that shows using the load balancing feature of {@link AsyncLoadBalancingClient}
 * 
 * @author awang
 *
 */
public class AsyncLoadBalancingClientExample {


    public static void main(String[] args) throws Exception {
        AsyncLoadBalancingHttpClient<ByteBuffer> client = AsyncHttpClientBuilder.withApacheAsyncClient()
                .balancingWithServerList(Lists.newArrayList(new Server("www.google.com", 80), new Server("www.microsoft.com", 80), new Server("www.yahoo.com", 80)))
                .build();
        try {
            HttpRequest request = HttpRequest.newBuilder().uri("/").build();
            for (int i = 0; i < 6; i++) {
                client.execute(request, new BufferedHttpResponseCallback() {

                    @Override
                    public void completed(HttpResponse response) {
                        System.out.println("Get response from server: " + response.getRequestedURI().getHost());
                    }

                    @Override
                    public void failed(Throwable e) {
                        System.err.println(e);
                    }

                    @Override
                    public void cancelled() {
                    }
                });
            }
            Thread.sleep(5000);
            System.out.println("Server stats: " + ((AbstractLoadBalancer) client.getLoadBalancer()).getLoadBalancerStats());

            ExecutionResult<HttpResponse> result = client.executeWithBackupRequests(request, 3, 100, TimeUnit.MILLISECONDS, null, new BufferedHttpResponseCallback() {
                @Override
                public void failed(Throwable e) {
                }

                @Override
                public void completed(HttpResponse response) {
                    System.out.println("Get first response from server: " + response.getRequestedURI().getHost());
                }

                @Override
                public void cancelled() {
                }
            });
            Thread.sleep(5000);
            System.out.println("URIs tried in execution with backup requests: " + result.getAllAttempts().keySet());
            System.out.println("Executed URI in execution with backup requests: " + result.getExecutedURI());
        } finally {
            client.close();
        }
    }
}
