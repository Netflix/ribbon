/*
 *
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.httpasyncclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncByteConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.netflix.client.AsyncClient;
import com.netflix.client.BufferedResponseCallback;
import com.netflix.client.ClientException;
import com.netflix.client.ResponseCallback;
import com.netflix.client.StreamDecoder;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.AsyncHttpClient;
import com.netflix.client.http.AsyncHttpClientBuilder;
import com.netflix.client.http.HttpRequest;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.JacksonSerializationFactory;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.Serializer;


/**
 * An asynchronous HTTP client implementation based on Apache's HttpAsyncClient.
 * <p>
 * By default, connection pooling is enabled and the {@link SerializationFactory} installed is the {@link JacksonSerializationFactory}. 
 * <p>
 * It is recommended to use {@link AsyncHttpClientBuilder} instead of using this class directly.
 * 
 * @author awang
 *
 */
public class RibbonHttpAsyncClient 
        implements AsyncClient<HttpRequest, com.netflix.client.http.HttpResponse, ByteBuffer, ContentTypeBasedSerializerKey>, 
                   AsyncHttpClient<ByteBuffer> {

    CloseableHttpAsyncClient httpclient;
    private List<SerializationFactory<ContentTypeBasedSerializerKey>> factories = Lists.newArrayList();
    private static Logger logger = LoggerFactory.getLogger(RibbonHttpAsyncClient.class);
            
    /**
     * Create an instance using default configuration obtained from {@link DefaultClientConfigImpl#getClientConfigWithDefaultValues()}
     */
    public RibbonHttpAsyncClient() {
        this(DefaultClientConfigImpl.getClientConfigWithDefaultValues());
    }
    
    /**
     * Create an instance with the passed in client configuration. To install a different default {@link SerializationFactory}, define property
     * {@link CommonClientConfigKey#DefaultSerializationFactoryClassName} in the configuration. An instance of {@link CloseableHttpAsyncClient} 
     * will be created and started.
     * 
     * @param clientConfig
     */
    @SuppressWarnings("unchecked")
    public RibbonHttpAsyncClient(IClientConfig clientConfig) {
        int connectTimeout = clientConfig.getPropertyAsInteger(CommonClientConfigKey.ConnectTimeout, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(clientConfig.getPropertyAsInteger(CommonClientConfigKey.ReadTimeout, DefaultClientConfigImpl.DEFAULT_READ_TIMEOUT))    
                .setConnectionRequestTimeout(connectTimeout)
                .build();
        httpclient = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(clientConfig.getPropertyAsInteger(CommonClientConfigKey.MaxTotalHttpConnections, DefaultClientConfigImpl.DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS))
                .setMaxConnPerRoute(clientConfig.getPropertyAsInteger(CommonClientConfigKey.MaxHttpConnectionsPerHost, DefaultClientConfigImpl.DEFAULT_MAX_HTTP_CONNECTIONS_PER_HOST))
                .build();
        String serializationFactoryClass = clientConfig.getPropertyAsString(CommonClientConfigKey.DefaultSerializationFactoryClassName, JacksonSerializationFactory.class.getName());
        if (serializationFactoryClass != null) {
            try {
                factories.add((SerializationFactory<ContentTypeBasedSerializerKey>) Class.forName(serializationFactoryClass).newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Unable to instantiate serialization factory", e);
            }            
        }
        httpclient.start();
    }

    public final List<? extends SerializationFactory<ContentTypeBasedSerializerKey>> getSerializationFactories() {
        return factories;
    }

    @Override
    public final void addSerializationFactory(SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory) {
        factories.add(0, serializationFactory);
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

    
    private Future<com.netflix.client.http.HttpResponse> createFuture(final Future<HttpResponse> future, final DelegateCallback<?> callback) {
        return new Future<com.netflix.client.http.HttpResponse>() {
            @Override
            public boolean cancel(boolean arg0) {
                return future.cancel(arg0);
            }

            @Override
            public HttpClientResponse get() throws InterruptedException,
                    ExecutionException {
                return callback.getCompletedResponse();
            }

            @Override
            public HttpClientResponse get(long time, TimeUnit timeUnit)
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
    public <E> Future<com.netflix.client.http.HttpResponse> execute(
            HttpRequest ribbonRequest, final StreamDecoder<E, ByteBuffer> decoder,
            final ResponseCallback<com.netflix.client.http.HttpResponse, E> callback)
            throws ClientException {        
        final HttpUriRequest request = getRequest(ribbonRequest);
        AbstractAsyncResponseConsumer<HttpResponse> consumer = null;
        if (decoder != null) {
            consumer = new AsyncByteConsumer<HttpResponse>() {
                private volatile HttpResponse response;
                ExpandableByteBuffer buffer = new ExpandableByteBuffer();
                @Override
                protected void onByteReceived(ByteBuffer buf, IOControl ioctrl)
                        throws IOException {
                    ByteBuffer bufferToConsume = buf;
                    if (buffer.hasContent()) {
                        logger.debug("internal buffer has unconsumed content");
                        while (buf.position() < buf.limit()) {
                            byte b = buf.get();
                            buffer.addByte(b);
                        }
                        bufferToConsume = buffer.getByteBuffer();
                        bufferToConsume.flip();
                    }
                    while (true) {
                        E obj = decoder.decode(bufferToConsume);
                        if (obj != null) {
                            if (callback != null) {
                                callback.contentReceived(obj);
                            }
                        } else {
                            break;
                        }
                    }
                    if (bufferToConsume.position() < bufferToConsume.limit()) {
                        // there are leftovers
                        logger.debug("copying leftover bytes not consumed by decoder to internal buffer for future use");
                        // discard bytes already consumed and copy over bytes not consumed to the new buffer
                        buffer = new ExpandableByteBuffer();
                        while (bufferToConsume.position() < bufferToConsume.limit()) {
                            byte b = bufferToConsume.get();
                            buffer.addByte(b);
                        }
                    } else if (bufferToConsume == buffer.getByteBuffer()) {
                        buffer.reset();
                    } 
                }
                
                @Override
                protected void onResponseReceived(HttpResponse response)
                        throws HttpException, IOException {
                    this.response = response;
                    if (callback != null) {
                        callback.responseReceived(new HttpClientResponse(response, factories, request.getURI(), this));
                    }
                }

                @Override
                protected HttpResponse buildResult(HttpContext context)
                        throws Exception {
                    return response;
                }            
            };
        } else {
            consumer = new BasicAsyncResponseConsumer() {
                @Override
                protected void onResponseReceived(HttpResponse response)
                        throws IOException {
                    super.onResponseReceived(response);
                    if (callback != null) {
                        callback.responseReceived(new HttpClientResponse(response, factories, request.getURI(), this));
                    }
                }
            };
        }
        DelegateCallback<E> fCallback = new DelegateCallback<E>(callback, request.getURI(), consumer);
        Future<HttpResponse> future = httpclient.execute(HttpAsyncMethods.create(request), consumer, fCallback);
        return createFuture(future, fCallback); 
    }
    
    @Override
    public Future<com.netflix.client.http.HttpResponse> execute(HttpRequest ribbonRequest, final BufferedResponseCallback<com.netflix.client.http.HttpResponse> callback) throws ClientException {
        return execute(ribbonRequest, null, callback);
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
            HttpEntity httpEntity = null;
            if (entity instanceof InputStream) {
                httpEntity = new InputStreamEntity((InputStream) entity, -1);
                builder.setEntity(httpEntity);
            } else {
                for (SerializationFactory<ContentTypeBasedSerializerKey> f: factories) {
                    Serializer serializer = f.getSerializer(key).orNull();
                    if (serializer != null) {
                        try {
                            ByteArrayOutputStream bout = new ByteArrayOutputStream();
                            serializer.serialize(bout, entity);
                            httpEntity = new ByteArrayEntity(bout.toByteArray());
                            builder.setEntity(httpEntity);
                            break;
                        } catch (IOException e) {
                            throw new ClientException(e);
                        }
                    }
                    
                }
                if (builder.getEntity() == null) {
                    throw new ClientException("No suitable serializer for " + key);
                }
            }
        }
        return builder.build();
    }
    
    class DelegateCallback<E> implements FutureCallback<HttpResponse> {
        private final ResponseCallback<com.netflix.client.http.HttpResponse, E> callback;
        private final AbstractAsyncResponseConsumer<HttpResponse> consumer;
        private AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        
        private URI requestedURI;
        
        public DelegateCallback(ResponseCallback<com.netflix.client.http.HttpResponse, E> callback, URI requestedURI, AbstractAsyncResponseConsumer<HttpResponse> consumer) {
            this.callback = callback;
            this.requestedURI = requestedURI;
            this.consumer = consumer;
        }
        
        private CountDownLatch latch = new CountDownLatch(1);
        private volatile HttpClientResponse completeResponse = null; 
        private volatile Throwable exception;
        
        HttpClientResponse getCompletedResponse() throws InterruptedException, ExecutionException {
            latch.await();
            if (completeResponse != null) {
                return completeResponse;
            } else if (exception != null) {
                throw new ExecutionException(exception);
            } else {
                throw new IllegalStateException("No response or exception is received");
            }
        }

        HttpClientResponse getCompletedResponse(long time, TimeUnit timeUnit) throws InterruptedException, TimeoutException, ExecutionException {
            if (latch.await(time, timeUnit)) {
                if (completeResponse != null) {
                    return completeResponse;
                } else if (exception != null) {
                    throw new ExecutionException(exception);
                } else {
                    throw new IllegalStateException("No response or exception is received");
                }
            } else {
                throw new TimeoutException();
            }
        }
        
        boolean isDone() {
            return latch.getCount() <= 0;
        }

        @Override
        public void completed(HttpResponse result) {
            if (callbackInvoked.compareAndSet(false, true)) {
                completeResponse = new HttpClientResponse(result, factories, requestedURI, consumer);
                latch.countDown();
                if (callback != null) {
                   try {
                        callback.completed(completeResponse);
                   } catch (Throwable e) {
                        logger.error("Error invoking callback", e);
                   }
                }
                try {
                    if (consumer instanceof AsyncByteConsumer) {
                        consumer.close();
                    }
                } catch (Throwable e) {
                    logger.error("Error closing stream", e);
                }
            }
        }

        @Override
        public void failed(Exception e) {
            if (callbackInvoked.compareAndSet(false, true)) {
                exception = e;
                latch.countDown();
                if (callback != null) {
                    callback.failed(e);
                }
            }
        }

        @Override
        public void cancelled() {
            if (callbackInvoked.compareAndSet(false, true)) {
                latch.countDown();
                if (callback != null) {
                    callback.cancelled();
                }
            }
        }
    }
    
    /**
     * Close the wrapped {@link CloseableHttpAsyncClient}
     */
    public void close() throws IOException {
        httpclient.close();
    }

    @Override
    public Future<com.netflix.client.http.HttpResponse> execute(
            HttpRequest request) throws ClientException {
        return execute(request, null);
    }
}
