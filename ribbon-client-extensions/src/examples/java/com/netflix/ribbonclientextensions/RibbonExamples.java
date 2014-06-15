package com.netflix.ribbonclientextensions;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

import com.netflix.client.config.ClientConfigBuilder;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.RibbonTransport;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbonclientextensions.ResponseTransformer;
import com.netflix.ribbonclientextensions.Ribbon;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;


public class RibbonExamples {
    public static void main(String[] args) {
        IClientConfig config = ClientConfigBuilder.newBuilderWithArchaiusProperties("myclient").build();
        HttpClient<ByteBuf, ByteBuf> transportClient = RibbonTransport.newHttpClient(config);
        HttpRequestTemplate<ByteBuf, ByteBuf> template = Ribbon.newHttpRequestTemplate(transportClient)
        .withNetworkResponseTransformer(new ResponseTransformer<HttpClientResponse<ByteBuf>>() {

            @Override
            public HttpClientResponse<ByteBuf> call(
                    HttpClientResponse<ByteBuf> t1) {
                if (t1.getStatus().code() >= 500) {
                    throw new RuntimeException("Unexpected response");
                }
                return t1;
            }
        })   
        .withFallbackProvider(new FallbackHandler<ByteBuf>() {
            @Override
            public Observable<ByteBuf> call(HystrixObservableCommand<ByteBuf> t1) {
                return Observable.empty();
            }
        })
        .withHystrixCommandPropertiesDefaults((HystrixCommandProperties.Setter().withExecutionIsolationThreadTimeoutInMilliseconds(2000)))
        .withUri("/{id}");
        
        template.requestBuilder().withValue("id", 1).build().execute();
        
        // example showing the use case of getting the entity with Hystrix meta data
        template.withUri("/{id}").requestBuilder().withValue("id", 3).build().withMetadata().toObservable()
        .subscribe(new Action1<RibbonResponse<ByteBuf>>(){
            @Override
            public void call(RibbonResponse<ByteBuf> t1) {
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
