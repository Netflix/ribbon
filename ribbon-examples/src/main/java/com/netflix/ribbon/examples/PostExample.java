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
import java.util.concurrent.Future;

import com.netflix.client.http.AsyncBufferingHttpClient;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.BufferedHttpResponseCallback;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.ribbon.examples.server.ServerResources.Person;

/**
 * An example that shows how serialization works in a POST request
 * @author awang
 *
 */
public class PostExample extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        AsyncBufferingHttpClient client = AsyncHttpClientBuilder.withApacheAsyncClient().buildBufferingClient();
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        Person myPerson = new Person("Example", 5);
        HttpRequest request = HttpRequest.newBuilder().uri(uri).verb(Verb.POST).entity(myPerson).header("Content-type", "application/json").build();
        try {
            Future<HttpResponse> response = client.execute(request, new BufferedHttpResponseCallback() {
                @Override
                public void failed(Throwable e) {
                    e.printStackTrace();
                }

                @Override
                public void completed(HttpResponse response) {
                    try {
                        System.out.println("Person uploaded: " + response.getEntity(Person.class));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void cancelled() {
                }
            });
            response.get();
        } finally {
            client.close();
        }
    }

    public static void main(String[] args) throws Exception {
        PostExample app = new PostExample();
        app.runApp();
    }
}
