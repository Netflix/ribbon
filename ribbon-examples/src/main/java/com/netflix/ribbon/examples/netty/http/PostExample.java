package com.netflix.ribbon.examples.netty.http;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpRequest;

import java.net.URI;

import rx.util.functions.Action1;

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.netty.http.NettyHttpClient;
import com.netflix.client.netty.http.NettyHttpClientBuilder;
import com.netflix.ribbon.examples.ExampleAppWithLocalResource;
import com.netflix.ribbon.examples.server.ServerResources.Person;
import com.netflix.serialization.JacksonCodec;
import com.netflix.serialization.SerializationUtils;
import com.netflix.serialization.TypeDef;

public class PostExample extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        Person myPerson = new Person("netty", 5);
        HttpRequest<ByteBuf> request = HttpRequest.createPost(SERVICE_URI + "testAsync/person").withHeader("Content-type", "application/json")
                .withContent(SerializationUtils.serializeToBytes(JacksonCodec.getInstance(), myPerson, null));
        NettyHttpClient observableClient = NettyHttpClientBuilder.newBuilder().withClientConfig(DefaultClientConfigImpl.getClientConfigWithDefaultValues()
                .setPropertyWithType(IClientConfigKey.CommonKeys.ReadTimeout, 10000)
                .setPropertyWithType(IClientConfigKey.CommonKeys.ConnectTimeout, 2000))
                .build();
        observableClient.createEntityObservable("localhost", port, request, TypeDef.fromClass(Person.class), null).toBlockingObservable().forEach(new Action1<Person>() {
            @Override
            public void call(Person t1) {
                try {
                    System.out.println(t1);
                } catch (Exception e) { // NOPMD
                }
            }
        });
    }
    
    public static void main(String[] args) throws Exception {
        new PostExample().runApp();
    }

}
