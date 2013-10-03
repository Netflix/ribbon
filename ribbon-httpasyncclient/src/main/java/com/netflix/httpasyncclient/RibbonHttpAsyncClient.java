package com.netflix.httpasyncclient;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    
    public static class AsyncResponse implements ResponseWithTypedEntity {

        private HttpResponse response;
        private SerializationFactory<ContentTypeBasedSerializerKey>  factory;
        
        public AsyncResponse(HttpResponse response, SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory) {
            this.response = response;    
            this.factory = serializationFactory;
        }
        
        @Override
        public Object getPayload() throws ClientException {
            return response.getEntity();
        }

        @Override
        public boolean hasPayload() {
            // return decoder != null && !decoder.isCompleted();
            
            HttpEntity entity = response.getEntity();
            try {
                return (entity != null && entity.getContent() != null && entity.getContent().available() > 0);
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public boolean isSuccess() {
            return response.getStatusLine().getStatusCode() == 200;
        }

        @Override
        public URI getRequestedURI() {
            return null;
        }

        @Override
        public Map<String, Collection<String>> getHeaders() {
            Multimap<String, String> map = ArrayListMultimap.create();
            for (Header header: response.getAllHeaders()) {
                map.put(header.getName(), header.getValue());
            }
            return map.asMap();
            
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
        
        public int getStatus() {
            return response.getStatusLine().getStatusCode();
        }
        
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

    
    private Future<AsyncResponse> fromHttpResponseFuture(final Future<HttpResponse> future) {
        return new Future<AsyncResponse>() {
            @Override
            public boolean cancel(boolean arg0) {
                return future.cancel(arg0);
            }

            @Override
            public AsyncResponse get() throws InterruptedException,
                    ExecutionException {
                return new AsyncResponse(future.get(), factory);
            }

            @Override
            public AsyncResponse get(long arg0, TimeUnit arg1)
                    throws InterruptedException, ExecutionException,
                    TimeoutException {
                return new AsyncResponse(future.get(arg0, arg1), factory);
            }

            @Override
            public boolean isCancelled() {
                return future.isCancelled();
            }

            @Override
            public boolean isDone() {
                return future.isDone();
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
        return fromHttpResponseFuture(future); 
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

        @Override
        public void completed(HttpResponse result) {
            if (callbackInvoked.compareAndSet(false, true)) {
                try {
                    callback.onResponseReceived(new AsyncResponse(result, factory));
                } catch (Throwable e) {
                    e.printStackTrace();
                    logger.error("Error invoking callback");
                } finally {
                    try {
                        result.getEntity().getContent().close();
                    } catch (Exception e) {
                    }
                }
            }
        }

        @Override
        public void failed(Exception e) {
            if (callbackInvoked.compareAndSet(false, true)) {
                callback.onException(e);
            }
        }

        @Override
        public void cancelled() {
            if (callbackInvoked.compareAndSet(false, true)) {
                callback.onException(new ClientException("request has been cancelled"));
            }
        }
    }

    @Override
    public <E> Future<AsyncResponse> stream(
            HttpRequest ribbonRequest,
            final StreamDecoder<E, ByteBuffer> decoder,
            final StreamCallback<AsyncResponse, E> callback) throws ClientException {
        HttpUriRequest request = getRequest(ribbonRequest);
        AsyncByteConsumer<HttpResponse> consumer = new AsyncByteConsumer<HttpResponse>() {
            private volatile HttpResponse response;            
            @Override
            protected void onByteReceived(ByteBuffer buf, IOControl ioctrl)
                    throws IOException {
                List<E> elements = decoder.decode(buf);
                if (elements != null) {
                    for (E e: elements) {
                        callback.onElement(e);
                    }
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
        
        final FutureCallback<HttpResponse> internalCallback = new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse result) {
                callback.onCompleted();
            }

            @Override
            public void failed(Exception ex) {
                callback.onError(ex);
            }

            @Override
            public void cancelled() {
                callback.onError(new ClientException("cancelled"));
            }            
        };
        
        Future<HttpResponse> future = httpclient.execute(HttpAsyncMethods.create(request), consumer, internalCallback);
        return fromHttpResponseFuture(future);
    }
}
