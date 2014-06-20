package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.RequestTemplate.RequestBuilder;
import com.netflix.ribbonclientextensions.Ribbon;
import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate;
import com.netflix.ribbonclientextensions.typedclient.sample.Movie;
import com.netflix.ribbonclientextensions.typedclient.sample.SampleTypedMovieService;
import io.reactivex.netty.protocol.http.client.ContentSource;
import io.reactivex.netty.protocol.http.client.HttpClient;
import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Method;
import java.util.Map;

import static com.netflix.ribbonclientextensions.typedclient.ReflectUtil.*;
import static junit.framework.Assert.*;
import static org.easymock.EasyMock.*;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.*;
import static org.powermock.api.easymock.PowerMock.replay;

/**
 * @author Tomasz Bak
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(Ribbon.class)
public class MethodTemplateExecutorTest {

    @Test
    public void testGetWithParameterRequest() throws Exception {
        RibbonRequest ribbonRequestMock = createMock(RibbonRequest.class);

        RequestBuilder requestBuilderMock = createMock(RequestBuilder.class);
        expect(requestBuilderMock.withValue("id", "id123")).andReturn(requestBuilderMock);
        expect(requestBuilderMock.build()).andReturn(ribbonRequestMock);

        HttpClient httpClientMock = createMock(HttpClient.class);

        HttpRequestTemplate httpRequestTemplateMock = createMock(HttpRequestTemplate.class);
        expect(httpRequestTemplateMock.withMethod("GET")).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withUri("/movies/{id}")).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.requestBuilder()).andReturn(requestBuilderMock);

        mockStatic(Ribbon.class);
        expect(Ribbon.newHttpRequestTemplate("findMovieById", httpClientMock)).andReturn(httpRequestTemplateMock);

        replay(Ribbon.class, ribbonRequestMock, requestBuilderMock, httpClientMock, httpRequestTemplateMock);

        MethodTemplate methodTemplate = new MethodTemplate(methodByName(SampleTypedMovieService.class, "findMovieById"));
        MethodTemplateExecutor generator = new MethodTemplateExecutor(methodTemplate);

        RibbonRequest ribbonRequest = generator.executeFromTemplate(httpClientMock, new Object[]{"id123"});

        assertEquals(ribbonRequestMock, ribbonRequest);
    }

    @Test
    public void testPostWithContentParameter() throws Exception {
        RibbonRequest ribbonRequestMock = createMock(RibbonRequest.class);
        HttpClient httpClientMock = createMock(HttpClient.class);

        RequestBuilder requestBuilderMock = createMock(RequestBuilder.class);
        expect(requestBuilderMock.build()).andReturn(ribbonRequestMock);

        HttpRequestTemplate httpRequestTemplateMock = createMock(HttpRequestTemplate.class);
        expect(httpRequestTemplateMock.withMethod("POST")).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withUri("/movies")).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withContentSource((ContentSource) EasyMock.anyObject())).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.requestBuilder()).andReturn(requestBuilderMock);

        mockStatic(Ribbon.class);
        expect(Ribbon.newHttpRequestTemplate("registerMovie", httpClientMock)).andReturn(httpRequestTemplateMock);

        replay(Ribbon.class, ribbonRequestMock, requestBuilderMock, httpClientMock, httpRequestTemplateMock);

        MethodTemplate methodTemplate = new MethodTemplate(methodByName(SampleTypedMovieService.class, "registerMovie"));
        MethodTemplateExecutor generator = new MethodTemplateExecutor(methodTemplate);

        RibbonRequest ribbonRequest = generator.executeFromTemplate(httpClientMock, new Object[]{new Movie()});

        assertEquals(ribbonRequestMock, ribbonRequest);
    }

    @Test
    public void testFromFactory() throws Exception {
        Map<Method, MethodTemplateExecutor<Object>> executorMap = MethodTemplateExecutor.from(SampleTypedMovieService.class);

        assertEquals(3, executorMap.size());
    }
}