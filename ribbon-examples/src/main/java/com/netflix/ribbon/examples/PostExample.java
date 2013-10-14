package com.netflix.ribbon.examples;

import java.net.URI;
import java.util.concurrent.Future;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;

import com.netflix.client.http.AsyncBufferingHttpClient;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.BufferedHttpResponseCallback;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.ribbon.examples.server.ServerResources.Person;

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
        LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
        PostExample app = new PostExample();
        app.runApp();
    }
}
