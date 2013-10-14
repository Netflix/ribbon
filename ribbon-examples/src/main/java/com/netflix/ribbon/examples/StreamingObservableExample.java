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
