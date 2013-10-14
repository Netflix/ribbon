package com.netflix.ribbon.examples;

import java.net.URI;
import java.util.concurrent.Future;

import com.netflix.client.http.AsyncBufferingHttpClient;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.BufferedHttpResponseCallback;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.ribbon.examples.server.ServerResources.Person;


public class GetWithDeserialization extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        AsyncBufferingHttpClient client = AsyncHttpClientBuilder.withApacheAsyncClient().buildBufferingClient();
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
    }

    public static void main(String[] args) throws Exception {
        GetWithDeserialization app = new GetWithDeserialization();
        app.runApp();
    }
}
