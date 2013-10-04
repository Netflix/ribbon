package com.netflix.httpasyncclient;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncByteConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.netflix.client.AsyncClient;
import com.netflix.client.AsyncStreamClient;
import com.netflix.client.ClientException;
import com.netflix.client.ResponseCallback;
import com.netflix.client.ResponseWithTypedEntity;
import com.netflix.client.StreamDecoder;
import com.netflix.client.StreamResponseCallback;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.JacksonSerializationFactory;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.Serializer;

public class RibbonHttpAsyncClient 
    implements AsyncClient<HttpRequest, RibbonHttpAsyncClient.AsyncResponse>, 
               AsyncStreamClient<HttpRequest, RibbonHttpAsyncClient.AsyncResponse, ByteBuffer> {

    CloseableHttpAsyncClient httpclient;
    private SerializationFactory<ContentTypeBasedSerializerKey> factory = new JacksonSerializationFactory();
    private static Logger logger = LoggerFactory.getLogger(RibbonHttpAsyncClient.class);
    
    public static class AsyncResponse extends BaseResponse implements ResponseWithTypedEntity {

        private SerializationFactory<ContentTypeBasedSerializerKey>  factory;
        
        public AsyncResponse(HttpResponse response, SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory) {
            super(response);
            this.response = response;    
            this.factory = serializationFactory;
        }
        
        @Override
        public boolean hasPayload() {
            HttpEntity entity = response.getEntity();
            try {
                return (entity != null && entity.getContent() != null && entity.getContent().available() > 0);
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public <T> T get(Class<T> type) throws ClientException {
            ContentTypeBasedSerializerKey key = new ContentTypeBasedSerializerKey(response.getFirstHeader("Content-type").getValue(), type);
            Deserializer deserializer = factory.getDeserializer(key).orNull();
            try {
                return deserializer.deserialize(response.getEntity().getContent(), type);
            } catch (IOException e) {
                throw new ClientException(e);
            }
        }

        @Override
        public <T> T get(TypeToken<T> type) throws ClientException {
            ContentTypeBasedSerializerKey key = new ContentTypeBasedSerializerKey(response.getFirstHeader("Content-type").getValue(), type);
            Deserializer deserializer = factory.getDeserializer(key).orNull();
            try {
                return deserializer.deserialize(response.getEntity().getContent(), type);
            } catch (IOException e) {
                throw new ClientException(e);
            }

        }
        
        @Override
        public boolean hasEntity() {
            return hasPayload();
        }

        @Override
        public String getAsString() throws ClientException {
            ContentTypeBasedSerializerKey key = new ContentTypeBasedSerializerKey(response.getFirstHeader("Content-type").getValue(), String.class);
            Deserializer deserializer = factory.getDeserializer(key).orNull();
            try {
                return deserializer.deserializeAsString(response.getEntity().getContent());
            } catch (IOException e) {
                throw new ClientException(e);
            }
        }
        
        public void releaseResources() {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try {
                    entity.getContent().close();
                } catch (IllegalStateException e) {
                } catch (IOException e) {
                }
            }
        }
        
    }
    
    public RibbonHttpAsyncClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setSocketTimeout(10000)                
                .build();
        httpclient = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig).setMaxConnTotal(200)
                .setMaxConnPerRoute(50).build();
        httpclient.start();
    }
    
    public RibbonHttpAsyncClient(IClientConfig clientConfig) {
        int connectTimeout = clientConfig.getPropertyAsInteger(CommonClientConfigKey.ConnectTimeout, 10000);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(clientConfig.getPropertyAsInteger(CommonClientConfigKey.ReadTimeout, 10000))    
                .setConnectionRequestTimeout(connectTimeout)
                .build();
        httpclient = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(clientConfig.getPropertyAsInteger(CommonClientConfigKey.MaxTotalHttpConnections, 200))
                .setMaxConnPerRoute(clientConfig.getPropertyAsInteger(CommonClientConfigKey.MaxHttpConnectionsPerHost, 50))
                .build();
        httpclient.start();
    }

    
    private static String getContentType(Map<String, Collection<String>> headers) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, Collection<String>> entry: headers.entrySet()) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase("content-type")) {
                Collection<String> values = entry.getValue();
                if (values != null && values.size() > 0) {
                    return values.iterator().next();
                }
            }
        }
        return null;
    }

    
    private Future<AsyncResponse> createFuture(final Future<HttpResponse> future, final DelegateCallback callback) {
        return new Future<AsyncResponse>() {
            @Override
            public boolean cancel(boolean arg0) {
                return future.cancel(arg0);
            }

            @Override
            public AsyncResponse get() throws InterruptedException,
                    ExecutionException {
                return callback.getCompletedResponse();
            }

            @Override
            public AsyncResponse get(long time, TimeUnit timeUnit)
                    throws InterruptedException, ExecutionException,
                    TimeoutException {
                return callback.getCompletedResponse(time, timeUnit);
            }

            @Override
            public boolean isCancelled() {
                return future.isCancelled();
            }

            @Override
            public boolean isDone() {
                return callback.isDone();
            }            
        }; 
    }
    
    @Override
    public Future<AsyncResponse> execute(HttpRequest ribbonRequest,
            final ResponseCallback<AsyncResponse> callback) throws ClientException {
        
        HttpUriRequest request = getRequest(ribbonRequest);
        DelegateCallback fCallback = new DelegateCallback(callback);
        // MyResponseConsumer consumer = new MyResponseConsumer(callback);
        // logger.info("start execute");
        Future<HttpResponse> future = httpclient.execute(request, fCallback);
        return createFuture(future, fCallback); 
    }
    
    private HttpUriRequest getRequest(HttpRequest ribbonRequest) throws ClientException {
        RequestBuilder builder = RequestBuilder.create(ribbonRequest.getVerb().toString());
        Object entity = ribbonRequest.getEntity();
        URI uri = ribbonRequest.getUri();
        builder.setUri(uri);
        if (ribbonRequest.getQueryParams() != null) {
            for (Map.Entry<String, Collection<String>> entry: ribbonRequest.getQueryParams().entrySet()) {
                String name = entry.getKey();
                for (String value: entry.getValue()) {
                    builder.addParameter(name, value);
                }
            }
        }
        if (ribbonRequest.getHeaders() != null) {
            for (Map.Entry<String, Collection<String>> entry: ribbonRequest.getHeaders().entrySet()) {
                String name = entry.getKey();
                for (String value: entry.getValue()) {
                    builder.addHeader(name, value);
                }
            }
        }
                
        if (entity != null) {
            String contentType = getContentType(ribbonRequest.getHeaders());    
            ContentTypeBasedSerializerKey key = new ContentTypeBasedSerializerKey(contentType, entity.getClass());
            Serializer serializer = factory.getSerializer(key).orNull();
            if (serializer == null) {
                throw new ClientException("Unable to find serializer for " + key);
            }
            byte[] content;
            try {
                content = serializer.serialize(entity);
            } catch (IOException e) {
                throw new ClientException("Error serializing entity in request", e);
            }
            ByteArrayEntity finalEntity = new ByteArrayEntity(content);
            builder.setEntity(finalEntity);
        }
        return builder.build();
        
    }
    
    class DelegateCallback implements FutureCallback<HttpResponse> {
        private final ResponseCallback<AsyncResponse> callback;
        
        private AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        
        public DelegateCallback(ResponseCallback<AsyncResponse> callback) {
            this.callback = callback;
        }
        
        private CountDownLatch latch = new CountDownLatch(1);
        private volatile AsyncResponse completeResponse = null; 
        
        AsyncResponse getCompletedResponse() throws InterruptedException {
            latch.await();
            return completeResponse;
        }

        AsyncResponse getCompletedResponse(long time, TimeUnit timeUnit) throws InterruptedException {
            latch.await(time, timeUnit);
            return completeResponse;
        }
        
        boolean isDone() {
            return latch.getCount() <= 0;
        }

        @Override
        public void completed(HttpResponse result) {
            if (callbackInvoked.compareAndSet(false, true)) {
                completeResponse = new AsyncResponse(result, factory);
                latch.countDown();
                if (callback != null) {
                   try {
                        callback.completed(completeResponse);
                   } catch (Throwable e) {
                        logger.error("Error invoking callback", e);
                   } 
                }
            }
        }

        @Override
        public void failed(Exception e) {
            if (callbackInvoked.compareAndSet(false, true)) {
                latch.countDown();
                if (callback != null) {
                    callback.failed(e);
                }
            }
        }

        @Override
        public void cancelled() {
            if (callbackInvoked.compareAndSet(false, true) && callback != null) {
                callback.failed(new ClientException("request has been cancelled"));
            }
        }
    }

    @Override
    public <T> Future<AsyncResponse> stream(
            HttpRequest ribbonRequest,
            final StreamDecoder<T, ByteBuffer> decoder,
            final StreamResponseCallback<AsyncResponse, T> callback) throws ClientException {
        HttpUriRequest request = getRequest(ribbonRequest);
        final DelegateCallback internalCallback = new DelegateCallback(callback);
        AsyncByteConsumer<HttpResponse> consumer = new AsyncByteConsumer<HttpResponse>() {
            private volatile HttpResponse response;            
            @Override
            protected void onByteReceived(ByteBuffer buf, IOControl ioctrl)
                    throws IOException {
                T obj = decoder.decode(buf);
                if (obj != null) {
                    callback.onContentReceived(obj);
                }
            }

            @Override
            protected void onResponseReceived(HttpResponse response)
                    throws HttpException, IOException {
                this.response = response;
                callback.onResponseReceived(new AsyncResponse(response, factory));
            }

            @Override
            protected HttpResponse buildResult(HttpContext context)
                    throws Exception {
                return response;
            }            
        };
        
        
        Future<HttpResponse> future = httpclient.execute(HttpAsyncMethods.create(request), consumer, internalCallback);
        return createFuture(future, internalCallback);
    }
}
