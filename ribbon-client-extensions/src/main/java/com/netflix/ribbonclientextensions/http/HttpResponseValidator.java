package com.netflix.ribbonclientextensions.http;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import com.netflix.ribbonclientextensions.ResponseValidator;

public interface HttpResponseValidator extends ResponseValidator<HttpClientResponse<ByteBuf>> {
}
