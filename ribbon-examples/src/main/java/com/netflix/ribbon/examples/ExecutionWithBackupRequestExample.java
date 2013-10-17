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
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import com.netflix.client.AsyncBackupRequestsExecutor;
import com.netflix.client.AsyncBackupRequestsExecutor.ExecutionResult;
import com.netflix.client.http.AsyncHttpClient;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.BufferedHttpResponseCallback;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

/**
 * An example shows how to use the {@link AsyncBackupRequestsExecutor}
 * 
 * @author awang
 *
 */
public class ExecutionWithBackupRequestExample {
    public static void main(String[] args) throws Exception {
        AsyncHttpClient<ByteBuffer> client = AsyncHttpClientBuilder.withApacheAsyncClient().buildClient();
        try {
            List<HttpRequest> requests = Lists.newArrayList(HttpRequest.newBuilder().uri("http://www.microsoft.com/").build(),
                    HttpRequest.newBuilder().uri("http://www.google.com/").build());
            System.out.println("Try with 100ms timeout");
            ExecutionResult<HttpResponse> results = AsyncBackupRequestsExecutor.executeWithBackupRequests(client, requests, 100, TimeUnit.MILLISECONDS, new BufferedHttpResponseCallback() {
                @Override
                public void failed(Throwable e) {
                }

                @Override
                public void completed(HttpResponse response) {
                    System.out.println("Get response from server: " + response.getRequestedURI().getHost());
                }

                @Override
                public void cancelled() {
                }
            });
            Thread.sleep(2000);
            System.out.println("URIs tried: " + results.getAllAttempts().keySet());
            System.out.println("Callback invoked on URI: " + results.getExecutedURI());
            
            System.out.println("Try with 2000ms timeout");
            results = AsyncBackupRequestsExecutor.executeWithBackupRequests(client, requests, 2000, TimeUnit.MILLISECONDS, new BufferedHttpResponseCallback() {
                @Override
                public void failed(Throwable e) {
                }

                @Override
                public void completed(HttpResponse response) {
                    System.out.println("Get response from server: " + response.getRequestedURI().getHost());
                }

                @Override
                public void cancelled() {
                }
            });
            Thread.sleep(2000);
            System.out.println("URIs tried: " + results.getAllAttempts().keySet());
            System.out.println("Callback invoked on URI: " + results.getExecutedURI());

        } finally {
            client.close();
        }
    }
}
