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

import java.util.concurrent.Future;

import com.netflix.client.http.AsyncBufferingHttpClient;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.BufferedHttpResponseCallback;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

/**
 * A simple async http client application that handles buffered response
 *  
 * @author awang
 *
 */
public class AsyncClientSampleApp {

    public static void main(String[] args) throws Exception {
        AsyncBufferingHttpClient client = AsyncHttpClientBuilder.withApacheAsyncClient()
                .buildBufferingClient();
        HttpRequest request = HttpRequest.newBuilder().uri("http://www.google.com/").build();
        try {
            Future<HttpResponse> future = client.execute(request, new BufferedHttpResponseCallback() {
                @Override
                public void failed(Throwable e) {
                    System.err.println("failed: " + e);
                }

                @Override
                public void completed(HttpResponse response) {
                    System.out.println("Get response: " + response.getStatus());
                    try {
                        response.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void cancelled() {
                    System.err.println("cancelled");
                }
            });
            // this is to make sure the application will not exit until there is a response
            HttpResponse response = future.get();
            System.out.println("Status from response " + response.getStatus());
        } finally {
            client.close();
        }
    }
}
