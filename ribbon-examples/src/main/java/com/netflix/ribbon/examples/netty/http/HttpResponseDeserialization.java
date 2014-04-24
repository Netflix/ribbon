package com.netflix.ribbon.examples.netty.http;

import com.netflix.ribbon.examples.ExampleAppWithLocalResource;

public class HttpResponseDeserialization extends ExampleAppWithLocalResource {

    @Override
    public void run() throws Exception {
        // TODO Auto-generated method stub
        
    }
/*
    @Override
    public void run() throws Exception {
        HttpRequest<ByteBuf> request = HttpRequest.createGet(SERVICE_URI + "testAsync/getXml");
        NettyHttpClient observableClient = NettyHttpClientBuilder.newBuilder()
                .build();
        observableClient.createFullHttpResponseObservable("localhost", port, request)
            .flatMap(new Func1<HttpResponse<ByteBuf>, Observable<Person>>() {
                @Override
                public Observable<Person> call(HttpResponse<ByteBuf> t1) {
                    return t1.getContent().map(new Func1<ByteBuf, Person>() {

                        @Override
                        public Person call(ByteBuf t1) {
                            try {
                                return XmlCodec.<Person>getInstance().deserialize(new ByteBufInputStream(t1), TypeDef.fromClass(Person.class));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        
                    });
                }
            }).toBlockingObservable()
            .forEach(new Action1<Person>() {
                @Override
                public void call(Person t1) {
                    try {
                        System.out.println(t1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
    }
    
    public static void main(String[] args) throws Exception {
        new HttpResponseDeserialization().runApp();
    }
*/
}

