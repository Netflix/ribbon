package com.netflix.ribbon.examples.netty.http;

import io.reactivex.netty.protocol.http.ObservableHttpResponse;
import io.reactivex.netty.protocol.text.sse.SSEEvent;

import java.util.List;

import rx.Observable;
import rx.util.functions.Action1;
import rx.util.functions.Func1;

import com.google.common.collect.Lists;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.netty.http.NettyHttpClient;
import com.netflix.client.netty.http.NettyHttpClientBuilder;
import com.netflix.client.netty.http.ServerSentEvent;
import com.netflix.ribbon.examples.ExampleAppWithLocalResource;
import com.netflix.ribbon.examples.server.ServerResources.Person;
import com.netflix.serialization.JacksonCodec;
import com.netflix.serialization.TypeDef;

public class SeverSentEventExample extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        // Get the events and parse each data line using Jackson deserializer
        IClientConfig overrideConfig = new DefaultClientConfigImpl().setPropertyWithType(CommonClientConfigKey.Deserializer, JacksonCodec.getInstance());
        HttpRequest request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/personStream")
                .build();
        NettyHttpClient observableClient = NettyHttpClientBuilder.newBuilder().build();
        final List<Person> result = Lists.newArrayList();
        observableClient.createServerSentEventEntityObservable(request, TypeDef.fromClass(Person.class), overrideConfig)
        .subscribe(new Action1<ServerSentEvent<Person>>() {
            @Override
            public void call(ServerSentEvent<Person> t1) {
                // System.out.println(t1);
                result.add(t1.getEntity());
            }
        }, new Action1<Throwable>() {

            @Override
            public void call(Throwable t1) {
                t1.printStackTrace();
            }
            
        });
        Thread.sleep(2000);
        System.out.println(result);
        
        // Get the events as raw string
        request = HttpRequest.newBuilder().uri(SERVICE_URI + "testAsync/stream")
                .build();
        observableClient.createServerSentEventObservable(request)
            .flatMap(new Func1<ObservableHttpResponse<SSEEvent>, Observable<SSEEvent>>() {
                @Override
                public Observable<SSEEvent> call(
                        ObservableHttpResponse<SSEEvent> t1) {
                    return t1.content();
                }
            }).toBlockingObservable()
            .forEach(new Action1<SSEEvent>(){

                @Override
                public void call(SSEEvent t1) {
                    System.out.println(t1);
                }
            });
    }
    
    public static void main(String[] args) throws Exception {
        new SeverSentEventExample().runApp();
    }

}
