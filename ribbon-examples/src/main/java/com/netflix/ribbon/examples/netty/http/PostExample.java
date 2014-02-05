package com.netflix.ribbon.examples.netty.http;

import java.net.URI;

import rx.util.functions.Action1;

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.client.netty.http.NettyHttpClient;
import com.netflix.client.netty.http.NettyHttpClientBuilder;
import com.netflix.ribbon.examples.ExampleAppWithLocalResource;
import com.netflix.ribbon.examples.server.ServerResources.Person;
import com.netflix.serialization.TypeDef;

public class PostExample extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        Person myPerson = new Person("netty", 5);
        HttpRequest request = HttpRequest.newBuilder().uri(uri).verb(Verb.POST).entity(myPerson).header("Content-type", "application/json").build();
        NettyHttpClient observableClient = NettyHttpClientBuilder.newBuilder().withClientConfig(DefaultClientConfigImpl.getClientConfigWithDefaultValues()
                .setPropertyWithType(IClientConfigKey.CommonKeys.ReadTimeout, 10000)
                .setPropertyWithType(IClientConfigKey.CommonKeys.ConnectTimeout, 2000))
                .build();
        observableClient.createEntityObservable(request, TypeDef.fromClass(Person.class)).toBlockingObservable().forEach(new Action1<Person>() {
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
