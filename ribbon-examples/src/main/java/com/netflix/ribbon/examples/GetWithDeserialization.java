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

import java.net.URI;
import java.util.List;
import java.util.concurrent.Future;

import com.google.common.reflect.TypeToken;
import com.netflix.client.AsyncClient;
import com.netflix.client.http.AsyncBufferingHttpClient;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.BufferedHttpResponseCallback;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.ribbon.examples.server.ServerResources.Person;

/**
 * An example that shows how deserialization work on the {@link AsyncClient}
 * 
 * @author awang
 *
 */
public class GetWithDeserialization extends ExampleAppWithLocalResource {

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SE_BAD_FIELD")
    @SuppressWarnings("serial")
    @Override
    public void run() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        AsyncBufferingHttpClient client = AsyncHttpClientBuilder.withApacheAsyncClient().buildBufferingClient();
        try {
            Future<HttpResponse> future = client.execute(request, new BufferedHttpResponseCallback() {
                @Override
                public void failed(Throwable e) {
                }

                @Override
                public void completed(HttpResponse response) {
                    try {
                        System.out.println(response.getEntity(Person.class));
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        response.close();
                    }
                }

                @Override
                public void cancelled() {
                }
            });
            future.get();
            request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/persons").build();
            future = client.execute(request, new BufferedHttpResponseCallback() {
                @Override
                public void failed(Throwable e) {
                }

                @Override
                public void completed(HttpResponse response) {
                    try {
                        System.out.println(response.getEntity(new TypeToken<List<Person>>(){}));
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        response.close();
                    }
                }

                @Override
                public void cancelled() {
                }
            });
            future.get();
        } finally {
            client.close();
        }
    }

    public static void main(String[] args) throws Exception {
        GetWithDeserialization app = new GetWithDeserialization();
        app.runApp();
    }
}
