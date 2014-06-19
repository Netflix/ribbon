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
import com.netflix.hystrix.HystrixCommandGroupKey;
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
        HttpRequestTemplate<ByteBuf, ByteBuf> template = Ribbon.newHttpRequestTemplate("GetUser", transportClient)
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
        .withHystrixProperties((HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("mygroup"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationThreadTimeoutInMilliseconds(2000))))
        .withUri("/{id}");
        
        template.requestBuilder().withValue("id", 1).build().execute();
        
        // example showing the use case of getting the entity with Hystrix meta data
        template.withUri("/{id}").requestBuilder().withValue("id", 3).build().withMetadata().observe()
            .flatMap(new Func1<RibbonResponse<Observable<ByteBuf>>, Observable<String>>() {
                @Override
                public Observable<String> call(RibbonResponse<Observable<ByteBuf>> t1) {
                    if (t1.getHystrixInfo().isResponseFromFallback()) {
                        return Observable.empty();
                    } 
                    return t1.content().map(new Func1<ByteBuf, String>(){
                        @Override
                        public String call(ByteBuf t1) {
                            return t1.toString();
                        }
                        
                    });
                }
            });
    }

}
