package com.netflix.ribbonclientextensions.http;

import static org.junit.Assert.*;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;

import org.junit.Test;

import com.netflix.ribbonclientextensions.Ribbon;

public class TemplateBuilderTest {
    @Test
    public void testVarReplacement() {
        HttpRequestTemplate<ByteBuf, ByteBuf> template = Ribbon.newHttpRequestTemplate("test", 
                RxNetty.createHttpClient("foo", 1234));
        template.withUri("/foo/{id}?name={name}");
        HttpClientRequest<ByteBuf> request = template
                .requestBuilder()
                .withValue("id", "3")
                .withValue("name", "netflix")
                .createClientRequest();
        assertEquals("/foo/3?name=netflix", request.getUri());
    }
}

