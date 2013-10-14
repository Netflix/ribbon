package com.netflix.client.http;

import com.netflix.client.AsyncClient;
import com.netflix.client.AsyncLoadBalancingClient;
import com.netflix.client.config.IClientConfig;
import com.netflix.serialization.ContentTypeBasedSerializerKey;

public class AsyncLoadBalancingHttpClient<T> 
        extends AsyncLoadBalancingClient<HttpRequest, HttpResponse, T, ContentTypeBasedSerializerKey>
        implements AsyncHttpClient<T> {
    
    public AsyncLoadBalancingHttpClient(AsyncClient<HttpRequest, HttpResponse, T, ContentTypeBasedSerializerKey> client, IClientConfig config) {
        super(client, config);
    }    
}
