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
import java.util.concurrent.Future;

import com.netflix.client.AsyncClient;
import com.netflix.client.ResponseCallback;
import com.netflix.client.http.AsyncHttpClient;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

/**
 * An example shows using the streaming API of the {@link AsyncClient}. The application expects 
 * Server-Sent Events from server.
 * 
 * @author awang
 *
 */
public class AsyncStreamingClientApp extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/stream").build();
        AsyncHttpClient<ByteBuffer> client = AsyncHttpClientBuilder.withApacheAsyncClient().buildClient();
        try {
            Future<HttpResponse> response = client.execute(request, new SSEDecoder(), new ResponseCallback<HttpResponse, List<String>>() {
                @Override
                public void completed(HttpResponse response) {
                }

                @Override
                public void failed(Throwable e) {
                    e.printStackTrace();
                }

                @Override
                public void contentReceived(List<String> element) {
                    System.out.println("Get content from server: " + element);
                }

                @Override
                public void cancelled() {
                }

                @Override
                public void responseReceived(HttpResponse response) {
                }
            });
            response.get().close();
        } finally {
            client.close();
        }
    }

    public static void main(String [] args) throws Exception {
        AsyncStreamingClientApp app = new AsyncStreamingClientApp();
        app.runApp();
    }
}
