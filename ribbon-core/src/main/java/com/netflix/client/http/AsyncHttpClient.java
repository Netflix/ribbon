package com.netflix.client.http;

import com.netflix.client.AsyncClient;
import com.netflix.serialization.ContentTypeBasedSerializerKey;

public interface AsyncHttpClient<T> extends AsyncClient<HttpRequest, HttpResponse, T, ContentTypeBasedSerializerKey>, AsyncBufferingHttpClient {
}
