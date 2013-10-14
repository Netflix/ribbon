package com.netflix.client.http;

import com.netflix.client.ResponseBufferingAsyncClient;
import com.netflix.serialization.ContentTypeBasedSerializerKey;

public interface AsyncBufferingHttpClient extends ResponseBufferingAsyncClient<HttpRequest, HttpResponse, ContentTypeBasedSerializerKey>{
}
