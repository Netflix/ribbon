package com.netflix.httpasyncclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collection;
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
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.netflix.client.AsyncClient;
import com.netflix.client.AsyncLoadBalancingClient;
import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.FullResponseCallback;
import com.netflix.client.ResponseCallback;
import com.netflix.client.StreamDecoder;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.JacksonSerializationFactory;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.Serializer;


public class RibbonHttpAsyncClient implements AsyncClient<HttpRequest, com.netflix.client.http.HttpResponse, ByteBuffer> {

    CloseableHttpAsyncClient httpclient;
    private SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory = new JacksonSerializationFactory();
    private static Logger logger = LoggerFactory.getLogger(RibbonHttpAsyncClient.class);
        
    public static AsyncLoadBalancingClient<HttpRequest, com.netflix.client.http.HttpResponse, ByteBuffer> createNamedLoadBalancingClientFromConfig(String name) 
            throws ClientException {
        IClientConfig config = ClientFactory.getNamedConfig(name);
        return createNamedLoadBalancingClientFromConfig(name, config);       
    }
    
    public static AsyncLoadBalancingClient<HttpRequest, com.netflix.client.http.HttpResponse, ByteBuffer> createNamedLoadBalancingClientFromConfig(String name, IClientConfig clientConfig) 
            throws ClientException {
        Preconditions.checkArgument(clientConfig.getClientName().equals(name));
        try {
            RibbonHttpAsyncClient client = new RibbonHttpAsyncClient(clientConfig);
            ILoadBalancer loadBalancer  = ClientFactory.registerNamedLoadBalancerFromclientConfig(name, clientConfig);
            AsyncLoadBalancingClient<HttpRequest, com.netflix.client.http.HttpResponse, ByteBuffer> loadBalancingClient = 
                    new AsyncLoadBalancingClient<HttpRequest, com.netflix.client.http.HttpResponse, ByteBuffer>(client, clientConfig);
            loadBalancingClient.setLoadBalancer(loadBalancer);
            loadBalancingClient.setErrorHandler(new HttpAsyncClientLoadBalancerErrorHandler());
            return loadBalancingClient;
        } catch (Throwable e) {
            throw new ClientException(ClientException.ErrorType.CONFIGURATION, 
                    "Unable to create client", e);
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
    
    @SuppressWarnings("unchecked")
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
        String serializationFactoryClass = clientConfig.getPropertyAsString(CommonClientConfigKey.SerializationFactoryClassName, null);
        if (serializationFactoryClass != null) {
            try {
                serializationFactory = (SerializationFactory<ContentTypeBasedSerializerKey>) Class.forName(serializationFactoryClass).newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Unable to instantiate serialization factory", e);
            }            
        }
        httpclient.start();
    }

    
    public final SerializationFactory<ContentTypeBasedSerializerKey> getSerializationFactory() {
        return serializationFactory;
    }

    public final void setSerializationFactory(
            SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory) {
        this.serializationFactory = serializationFactory;
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
        DelegateCallback<E> fCallback = new DelegateCallback<E>(callback, request.getURI());
        Future<HttpResponse> future = null;
        if (decoder != null) {
            AsyncByteConsumer<HttpResponse> consumer = new AsyncByteConsumer<HttpResponse>() {
                private volatile HttpResponse response;            
                @Override
                protected void onByteReceived(ByteBuffer buf, IOControl ioctrl)
                        throws IOException {
                    E obj = decoder.decode(buf);
                    if (obj != null && callback != null) {
                        callback.contentReceived(obj);
                    }
                }

                @Override
                protected void onResponseReceived(HttpResponse response)
                        throws HttpException, IOException {
                    this.response = response;
                    if (callback != null) {
                        callback.responseReceived(new HttpClientResponse(response, serializationFactory, request.getURI()));
                    }
                }

                @Override
                protected HttpResponse buildResult(HttpContext context)
                        throws Exception {
                    return response;
                }            
            };
            future = httpclient.execute(HttpAsyncMethods.create(request), consumer, fCallback);
        } else {
            BasicAsyncResponseConsumer consumer = new BasicAsyncResponseConsumer() {
                @Override
                protected void onResponseReceived(HttpResponse response)
                        throws IOException {
                    super.onResponseReceived(response);
                    if (callback != null) {
                        callback.responseReceived(new HttpClientResponse(response, serializationFactory, request.getURI()));
                    }
                }
            };
            future = httpclient.execute(HttpAsyncMethods.create(request), consumer, fCallback);
            
            // future = httpclient.execute(request, fCallback);

        }
        return createFuture(future, fCallback); 
    }
    
    public Future<com.netflix.client.http.HttpResponse> execute(HttpRequest ribbonRequest, final FullResponseCallback<com.netflix.client.http.HttpResponse> callback) throws ClientException {
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
                Serializer serializer = serializationFactory.getSerializer(key).orNull();
                if (serializer == null) {
                    throw new ClientException("Unable to find serializer for " + key);
                }
                PipedOutputStream source = new PipedOutputStream();
                try {
                    httpEntity = new InputStreamEntity(new PipedInputStream(source));
                    serializer.serialize(source, entity);
                    builder.setEntity(httpEntity);
                } catch (IOException e) {
                    throw new ClientException(e);
                }
            }
        }
        return builder.build();
    }
    
    class DelegateCallback<E> implements FutureCallback<HttpResponse> {
        private final ResponseCallback<com.netflix.client.http.HttpResponse, E> callback;
        
        private AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        
        private URI requestedURI;
        
        public DelegateCallback(ResponseCallback<com.netflix.client.http.HttpResponse, E> callback, URI requestedURI) {
            this.callback = callback;
            this.requestedURI = requestedURI;
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
                completeResponse = new HttpClientResponse(result, serializationFactory, requestedURI);
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
                exception = e;
                latch.countDown();
                if (callback != null) {
                    callback.failed(e);
                }
            }
        }

        @Override
        public void cancelled() {
            if (callbackInvoked.compareAndSet(false, true) && callback != null) {
                callback.cancelled();
            }
        }
    }
    
    public void close() throws IOException {
        httpclient.close();
    }
}
