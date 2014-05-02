package com.netlfix.resource;

import com.netflix.client.http.HttpRequest;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.ribbonclientextensions.*;
import com.netflix.ribbonclientextensions.evcache.EvCacheConfig;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;
import com.netflix.ribbonclientextensions.hystrix.FallbackResponse;
import com.sun.jersey.client.impl.ClientRequestImpl;
import org.apache.http.entity.ContentType;
import rx.Observable;

/**
 * Created by mcohen on 5/1/14.
 */
public class SampleClient {

    HttpService service = new HttpService();

    class SampleFallbackHandler<T> extends FallbackHandler<T> {

        public SampleFallbackHandler(String commandKey) {
            super(commandKey);
        }

        @Override
        protected FallbackResponse handleCommandException(HystrixRuntimeException exception) {
            return null;
        }

        @Override
        protected FallbackResponse handleShortCircuit(HystrixRuntimeException exception) {
            return null;
        }

        @Override
        protected FallbackResponse handleRejectedSemaphore(HystrixRuntimeException exception) {
            return null;
        }

        @Override
        protected FallbackResponse handleRejectedSemaphoreFallback(HystrixRuntimeException exception) {
            return null;
        }

        @Override
        protected FallbackResponse handleTimeout(HystrixRuntimeException exception) {
            return null;
        }
    }

      public String testV1Blocking(String id1, String id2, CustomerContext context) {

        ResourceTemplate<String> template = getTestV1Template();
        Resource<String> resource = template.resource();
        resource.addHeader("myCoolHeader", "moo!!");

        //data
        resource.withTarget("id1", id1);
        resource.withTarget("id2", id2);
        resource.withTarget("custId", context.customerId); //for CacheKey
        resource = resource.withCustomerContext(context);

        Observable<Response<String>> response = resource.execute();
        Response<String> r = response.toBlockingObservable().first();
        return r.getData();

    }

    private ResourceTemplate<String> getTestV1Template() {
        HttpService service = getHttpService();
        ResourceTemplate<String> template = new ResourceTemplate<String>(
                HttpRequest.Verb.GET, String.class,
                "/test/v1/{id1}/{id2}",
                service);

        EvCacheConfig cacheConfig = new EvCacheConfig(
                "test", //app name
                "myCache", //cache name
                true, //zone fallback
                36000, // ttl
                true, // touch on get
                "{custId}:{id1}");   // cacheKeyTemplate

        template.withPrimaryCache(cacheConfig);

        template = template.withPrimaryCache(cacheConfig);
        template = template.withFallbackHandler(new SampleFallbackHandler<String>("MyCommand"));

        //to handle concrete responses
        template = template.withResponseClass(String.class);
        return template;
    }

    private HttpService getHttpService() {
        if(service != null) return service;

        service = new HttpService();
        service.withRESTClientName("api").
                withSuccessHttpStatus(200,202,302,301).
                withHeader("test", "header").
                withContentType(ContentType.APPLICATION_JSON);
        return service;
    }
}
