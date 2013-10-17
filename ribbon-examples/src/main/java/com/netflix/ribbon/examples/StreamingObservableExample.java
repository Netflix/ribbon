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
import java.util.concurrent.atomic.AtomicReference;

import rx.util.functions.Action1;

import com.netflix.client.ObservableAsyncClient;
import com.netflix.client.ObservableAsyncClient.StreamEvent;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

/**
 * A streaming example with the {@link ObservableAsyncClient}
 * 
 * @author awang
 *
 */
public class StreamingObservableExample extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/stream").build();
        ObservableAsyncClient<HttpRequest, HttpResponse, ByteBuffer> observableClient = 
                AsyncHttpClientBuilder.withApacheAsyncClient().observableClient();
        final AtomicReference<HttpResponse> httpResponse = new AtomicReference<HttpResponse>(); 
        try {
            observableClient.stream(request, new SSEDecoder())
            .toBlockingObservable()
            .forEach(new Action1<StreamEvent<HttpResponse, List<String>>>() {
                @Override
                public void call(final StreamEvent<HttpResponse, List<String>> t1) {
                    System.out.println("Content from server: " + t1.getEvent());
                    httpResponse.set(t1.getResponse());
                }
            });
        } finally {
            httpResponse.get().close();
            observableClient.close();
        }
    }

    public static void main(String[] args) throws Exception {
        StreamingObservableExample app = new StreamingObservableExample();
        app.runApp();
    }
}
