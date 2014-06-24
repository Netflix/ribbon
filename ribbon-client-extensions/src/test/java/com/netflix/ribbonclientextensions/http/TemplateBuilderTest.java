package com.netflix.ribbonclientextensions.http;

import static org.junit.Assert.*;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;

import org.junit.Test;

import com.netflix.ribbonclientextensions.Ribbon;

public class TemplateBuilderTest {
    @Test
    public void testVarReplacement() {
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("test");
        
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("resource1", ByteBuf.class);
        template.withUri("/foo/{id}?name={name}");
        HttpClientRequest<ByteBuf> request = template
                .requestBuilder()
                .withRequestProperty("id", "3")
                .withRequestProperty("name", "netflix")
                .createClientRequest();
        assertEquals("/foo/3?name=netflix", request.getUri());
    }
}

