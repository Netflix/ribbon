package com.netflix.ribbon.examples.netty.http;

import rx.util.functions.Action1;

import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.netty.http.NettyHttpClient;
import com.netflix.client.netty.http.NettyHttpClientBuilder;
import com.netflix.ribbon.examples.ExampleAppWithLocalResource;
import com.netflix.ribbon.examples.server.ServerResources.Person;
import com.netflix.serialization.TypeDef;

public class HttpResponseDeserialization extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/getXml").build();
        NettyHttpClient observableClient = NettyHttpClientBuilder.newBuilder()
                .build();
        observableClient.createFullHttpResponseObservable(request)
            .toBlockingObservable()
            .forEach(new Action1<HttpResponse>() {
                @Override
                public void call(HttpResponse t1) {
                    try {
                        System.out.println(t1.getStatus());
                        System.out.println(t1.getEntity(TypeDef.fromClass(Person.class), new XmlCodec<Person>()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
    }
    
    public static void main(String[] args) throws Exception {
        new HttpResponseDeserialization().runApp();
    }

}

