package com.netflix.ribbon.examples;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Future;

import com.netflix.client.ResponseCallback;
import com.netflix.client.http.AsyncHttpClient;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

public class AsyncStreamingClientApp extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/stream").build();
        AsyncHttpClient<ByteBuffer> client = AsyncHttpClientBuilder.withApacheAsyncClient().buildClient();
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
    }
    
    public static void main(String [] args) throws Exception {
        AsyncStreamingClientApp app = new AsyncStreamingClientApp();
        app.runApp();
    }
}
