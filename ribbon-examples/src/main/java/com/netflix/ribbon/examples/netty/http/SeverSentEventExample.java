package com.netflix.ribbon.examples.netty.http;

import com.netflix.ribbon.examples.ExampleAppWithLocalResource;

public class SeverSentEventExample extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        /*
        // Get the events and parse each data line using Jackson deserializer
        IClientConfig overrideConfig = new DefaultClientConfigImpl().setPropertyWithType(CommonClientConfigKey.Deserializer, JacksonCodec.getInstance());
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/personStream");
        NettyHttpClient observableClient = NettyHttpClientBuilder.newBuilder().build();
        final List<Person> result = Lists.newArrayList();
        observableClient.createServerSentEventEntityObservable("localhost", port, request, TypeDef.fromClass(Person.class), overrideConfig)
        .subscribe(new Action1<ServerSentEventWithEntity<Person>>() {
            @Override
            public void call(ServerSentEventWithEntity<Person> t1) {
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
        request = HttpRequest.createGet(SERVICE_URI + "testAsync/stream");
        observableClient.createServerSentEventObservable("localhost", port, request)
            .flatMap(new Func1<HttpResponse<ServerSentEvent>, Observable<ServerSentEvent>>() {
                @Override
                public Observable<ServerSentEvent> call(
                        HttpResponse<ServerSentEvent> t1) {
                    return t1.getContent();
                }
            }).toBlockingObservable()
            .forEach(new Action1<ServerSentEvent>(){

                @Override
                public void call(ServerSentEvent t1) {
                    System.out.println(t1);
                }
            }); */
    }
    
    public static void main(String[] args) throws Exception {
        new SeverSentEventExample().runApp();
    }

}
