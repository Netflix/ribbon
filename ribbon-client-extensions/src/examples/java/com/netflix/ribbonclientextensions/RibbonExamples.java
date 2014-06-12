package com.netflix.ribbonclientextensions;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Action1;

import com.netflix.client.config.ClientConfigBuilder;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.RibbonTransport;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixCommandProperties.Setter;
import com.netflix.ribbonclientextensions.FallbackDeterminator;
import com.netflix.ribbonclientextensions.Ribbon;
import com.netflix.ribbonclientextensions.hystrix.FallbackProvider;
import com.netflix.ribbonclientextensions.hystrix.HystrixResponse;


public class RibbonExamples {
    public static void main(String[] args) {
        IClientConfig config = ClientConfigBuilder.newBuilderWithArchaiusProperties("myclient").build();
        HttpClient<ByteBuf, ByteBuf> transportClient = RibbonTransport.newHttpClient(config);
        Ribbon.from(transportClient)
        .newRequestTemplate()
        .withFallbackDeterminator(new FallbackDeterminator<HttpClientResponse<ByteBuf>>() {
            @Override
            public boolean shouldTriggerFallback(
                    HttpClientResponse<ByteBuf> response) {
                return response.getStatus().code() >= 500;
            }
        })   
        .withFallbackProvider(new FallbackProvider<ByteBuf>() {
            @Override
            public Observable<ByteBuf> call(HystrixCommand<ByteBuf> t1) {
                return Observable.empty();
            }
        })
        .withHystrixCollapserPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationThreadTimeoutInMilliseconds(2000))
        .withUri("/{id}")
        .requestBuilder().withValue("id", 1).build().execute();
        
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
