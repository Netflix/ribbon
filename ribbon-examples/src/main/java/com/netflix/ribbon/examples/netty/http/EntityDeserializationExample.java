package com.netflix.ribbon.examples.netty.http;

import com.netflix.ribbon.examples.ExampleAppWithLocalResource;

public class EntityDeserializationExample extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        /*
        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet(SERVICE_URI + "testAsync/person");
        NettyHttpClient observableClient = NettyHttpClientBuilder.newBuilder()
                .build();

        // deserialize using the default Jackson deserializer
        observableClient.createEntityObservable("localhost", port, request, TypeDef.fromClass(Person.class), null).toBlockingObservable().forEach(new Action1<Person>() {
            @Override
            public void call(Person t1) {
                try {
                    System.out.println("Person: " + t1);
                } catch (Exception e) { 
                    e.printStackTrace();
                }
            }
        });

        // deserialize as Map using the default Jackson deserializer
        observableClient.createEntityObservable("localhost", port, request, new TypeDef<Map<String, Object>>(){}, null)
        .toBlockingObservable()
        .forEach(new Action1<Map<String, Object>>() {
            @Override
            public void call(Map<String, Object> t1) {
                try {
                    System.out.println("Map: " + t1);
                } catch (Exception e) { 
                    e.printStackTrace();
                }
            }
        });

        // deserialize using Xml deserializer
        IClientConfig requestConfig = DefaultClientConfigImpl.getEmptyConfig()
                .setPropertyWithType(IClientConfigKey.CommonKeys.Deserializer, XmlCodec.<Person>getInstance());
        request = HttpRequest.createGet(SERVICE_URI + "testAsync/getXml");
        observableClient.createEntityObservable("localhost", port, request, TypeDef.fromClass(Person.class), requestConfig)
        .toBlockingObservable()
        .forEach(new Action1<Person>() {
            @Override
            public void call(Person t1) {
                try {
                    System.out.println("Person: " + t1);
                } catch (Exception e) { 
                    e.printStackTrace();
                }
            }
        });

        // URI does not exist, will get UnexpectedResponseException
        request = HttpRequest.createGet(SERVICE_URI + "testAsync/NotFound");
        observableClient.createEntityObservable("localhost", port, request, TypeDef.fromClass(Person.class), null)
        .subscribe(new Action1<Person>() {
            @Override
            public void call(Person t1) {
                try {
                    System.out.println("Person: " + t1);
                } catch (Exception e) { 
                    e.printStackTrace();
                }
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable t1) {
                if (t1 instanceof UnexpectedHttpResponseException) {
                    UnexpectedHttpResponseException ex = (UnexpectedHttpResponseException) t1;
                    System.out.println(ex.getStatusCode());
                }
            }

        });
        Thread.sleep(2000);
        */
    }

    public static void main(String[] args) throws Exception {
        new EntityDeserializationExample().runApp();
    }
    
}

