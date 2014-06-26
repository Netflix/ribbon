package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.http.HttpRequestBuilder;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate;
import com.netflix.ribbonclientextensions.http.HttpResourceGroup;
import com.netflix.ribbonclientextensions.typedclient.sample.MovieServiceInterfaces.SampleMovieService;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Method;
import java.util.Map;

import static com.netflix.ribbonclientextensions.typedclient.ReflectUtil.*;
import static junit.framework.Assert.*;
import static org.easymock.EasyMock.*;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.replay;

/**
 * @author Tomasz Bak
 */
@RunWith(PowerMockRunner.class)
public class MethodTemplateExecutorTest {

    @Test
    public void testGetWithParameterRequest() throws Exception {
        RibbonRequest ribbonRequestMock = createMock(RibbonRequest.class);

        HttpRequestBuilder requestBuilderMock = createMock(HttpRequestBuilder.class);
        expect(requestBuilderMock.withRequestProperty("id", "id123")).andReturn(requestBuilderMock);
        expect(requestBuilderMock.build()).andReturn(ribbonRequestMock);

        HttpRequestTemplate httpRequestTemplateMock = createMock(HttpRequestTemplate.class);
        expect(httpRequestTemplateMock.withMethod("GET")).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withUriTemplate("/movies/{id}")).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.requestBuilder()).andReturn(requestBuilderMock);

        HttpResourceGroup httpResourceGroupMock = createMock(HttpResourceGroup.class);
        expect(httpResourceGroupMock.newRequestTemplate("findMovieById")).andReturn(httpRequestTemplateMock);

        replay(ribbonRequestMock, requestBuilderMock, httpRequestTemplateMock, httpResourceGroupMock);

        MethodTemplate methodTemplate = new MethodTemplate(methodByName(SampleMovieService.class, "findMovieById"));

        MethodTemplateExecutor generator = new MethodTemplateExecutor(methodTemplate);

        RibbonRequest ribbonRequest = generator.executeFromTemplate(httpResourceGroupMock, new Object[]{"id123"});

        assertEquals(ribbonRequestMock, ribbonRequest);
    }

    @Test
    @Ignore
    public void testPostWithContentParameter() throws Exception {
//        RibbonRequest ribbonRequestMock = createMock(RibbonRequest.class);
//        HttpClient httpClientMock = createMock(HttpClient.class);
//
//        RequestBuilder requestBuilderMock = createMock(RequestBuilder.class);
//        expect(requestBuilderMock.build()).andReturn(ribbonRequestMock);
//
//        HttpRequestTemplate httpRequestTemplateMock = createMock(HttpRequestTemplate.class);
//        expect(httpRequestTemplateMock.withMethod("POST")).andReturn(httpRequestTemplateMock);
//        expect(httpRequestTemplateMock.withUri("/movies")).andReturn(httpRequestTemplateMock);
//        expect(httpRequestTemplateMock.withContentSource((ContentSource) EasyMock.anyObject())).andReturn(httpRequestTemplateMock);
//        expect(httpRequestTemplateMock.requestBuilder()).andReturn(requestBuilderMock);
//
//        mockStatic(Ribbon.class);
//        expect(Ribbon.newHttpRequestTemplate("registerMovie", httpClientMock)).andReturn(httpRequestTemplateMock);
//
//        replay(Ribbon.class, ribbonRequestMock, requestBuilderMock, httpClientMock, httpRequestTemplateMock);
//
//        MethodTemplate methodTemplate = new MethodTemplate(methodByName(SampleTypedMovieService.class, "registerMovie"));
//        MethodTemplateExecutor generator = new MethodTemplateExecutor(methodTemplate);
//
//        RibbonRequest ribbonRequest = generator.executeFromTemplate(httpClientMock, new Object[]{new Movie()});
//
//        assertEquals(ribbonRequestMock, ribbonRequest);
    }

    @Test
    public void testFromFactory() throws Exception {
        Map<Method, MethodTemplateExecutor<Object>> executorMap = MethodTemplateExecutor.from(SampleMovieService.class);

        assertEquals(3, executorMap.size());
    }
}