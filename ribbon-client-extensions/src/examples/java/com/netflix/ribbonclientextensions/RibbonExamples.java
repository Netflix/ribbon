package com.netflix.ribbonclientextensions;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import rx.functions.Action1;

import com.netflix.client.config.ClientConfigBuilder;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.RibbonTransport;
import com.netflix.ribbonclientextensions.Ribbon;
import com.netflix.ribbonclientextensions.hystrix.HystrixResponse;


public class RibbonExamples {
    public static void main(String[] args) {
        IClientConfig config = ClientConfigBuilder.newBuilderWithArchaiusProperties("myclient").build();
        HttpClient<ByteBuf, ByteBuf> transportClient = RibbonTransport.newHttpClient(config);
        Ribbon.from(transportClient).newRequestTemplate()
        .withUri("/{id}").requestBuilder().withValue("id", 1).build().execute();
        
        // example showing the use case of getting the entity with Hystrix meta data
        Ribbon.from(transportClient).newRequestTemplate()
        .withUri("/{id}").requestBuilder().withValue("id", 1).build().withHystrixInfo().toObservable()
        .subscribe(new Action1<HystrixResponse<ByteBuf>>(){
            @Override
            public void call(HystrixResponse<ByteBuf> t1) {
                System.out.println(t1.getHystrixInfo());
                t1.toObservable().toBlocking().forEach(new Action1<ByteBuf>() {
                    @Override
                    public void call(ByteBuf t1) {
                    }
                });
            }
        });
    }

}
