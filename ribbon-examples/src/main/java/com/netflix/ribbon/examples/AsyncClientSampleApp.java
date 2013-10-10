package com.netflix.ribbon.examples;

import java.util.concurrent.Future;

import com.netflix.client.FullResponseCallback;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.httpasyncclient.RibbonHttpAsyncClient;

public class AsyncClientSampleApp {
    
    public static void main(String[] args) throws Exception {
        RibbonHttpAsyncClient client = new RibbonHttpAsyncClient();
        HttpRequest request = HttpRequest.newBuilder().uri("http://www.google.com/").build();
        Future<HttpResponse> future = client.execute(request, new FullResponseCallback<HttpResponse>() {
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
        Thread.sleep(2000);
        if (future.isDone()) {
            client.close();
        }
    }
}
