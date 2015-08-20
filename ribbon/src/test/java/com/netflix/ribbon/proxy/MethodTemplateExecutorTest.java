/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon.proxy;

import com.netflix.ribbon.CacheProvider;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.http.HttpRequestBuilder;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpRequestTemplate.Builder;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider;
import com.netflix.ribbon.proxy.sample.HystrixHandlers.MovieFallbackHandler;
import com.netflix.ribbon.proxy.sample.HystrixHandlers.SampleHttpResponseValidator;
import com.netflix.ribbon.proxy.sample.Movie;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieService;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.ShortMovieService;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.channel.ContentTransformer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import rx.Observable;

import java.lang.reflect.Method;
import java.util.Map;

import static com.netflix.ribbon.proxy.Utils.methodByName;
import static junit.framework.Assert.assertEquals;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.*;

/**
 * @author Tomasz Bak
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MethodTemplateExecutor.class})
@PowerMockIgnore("javax.management.*")
public class MethodTemplateExecutorTest {

    @Mock
    private RibbonRequest ribbonRequestMock = createMock(RibbonRequest.class);

    @Mock
    private HttpRequestBuilder requestBuilderMock = createMock(HttpRequestBuilder.class);

    @Mock
    private Builder httpRequestTemplateBuilderMock = createMock(Builder.class);

    @Mock
    private HttpRequestTemplate httpRequestTemplateMock = createMock(HttpRequestTemplate.class);

    @Mock
    private HttpResourceGroup httpResourceGroupMock = createMock(HttpResourceGroup.class);

    @BeforeClass
    public static void setup() {
        RibbonDynamicProxy.registerAnnotationProcessors(AnnotationProcessorsProvider.DEFAULT);
    }

    @Before
    public void setUp() throws Exception {
        expect(requestBuilderMock.build()).andReturn(ribbonRequestMock);
        expect(httpRequestTemplateBuilderMock.build()).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.requestBuilder()).andReturn(requestBuilderMock);
    }

    @Test
    public void testGetQueryWithDomainObjectResult() throws Exception {
        expectUrlBase("GET", "/movies/{id}");

        expect(requestBuilderMock.withRequestProperty("id", "id123")).andReturn(requestBuilderMock);
        expect(httpResourceGroupMock.newTemplateBuilder("findMovieById")).andReturn(httpRequestTemplateBuilderMock);

        expect(httpRequestTemplateBuilderMock.withHeader("X-MyHeader1", "value1.1")).andReturn(httpRequestTemplateBuilderMock);
        expect(httpRequestTemplateBuilderMock.withHeader("X-MyHeader1", "value1.2")).andReturn(httpRequestTemplateBuilderMock);
        expect(httpRequestTemplateBuilderMock.withHeader("X-MyHeader2", "value2")).andReturn(httpRequestTemplateBuilderMock);
        expect(httpRequestTemplateBuilderMock.withRequestCacheKey("findMovieById/{id}")).andReturn(httpRequestTemplateBuilderMock);
        expect(httpRequestTemplateBuilderMock.withFallbackProvider(anyObject(MovieFallbackHandler.class))).andReturn(httpRequestTemplateBuilderMock);
        expect(httpRequestTemplateBuilderMock.withResponseValidator(anyObject(SampleHttpResponseValidator.class))).andReturn(httpRequestTemplateBuilderMock);
        expect(httpRequestTemplateBuilderMock.withCacheProvider(anyObject(String.class), anyObject(CacheProvider.class))).andReturn(httpRequestTemplateBuilderMock);

        replayAll();

        MethodTemplateExecutor executor = createExecutor(SampleMovieService.class, "findMovieById");
        RibbonRequest ribbonRequest = executor.executeFromTemplate(new Object[]{"id123"});

        verifyAll();

        assertEquals(ribbonRequestMock, ribbonRequest);
    }
    @Test
    public void testGetQueryWithByteBufResult() throws Exception {
        expectUrlBase("GET", "/rawMovies/{id}");

        expect(requestBuilderMock.withRequestProperty("id", "id123")).andReturn(requestBuilderMock);
        expect(httpResourceGroupMock.newTemplateBuilder("findRawMovieById")).andReturn(httpRequestTemplateBuilderMock);

        replayAll();

        MethodTemplateExecutor executor = createExecutor(SampleMovieService.class, "findRawMovieById");
        RibbonRequest ribbonRequest = executor.executeFromTemplate(new Object[]{"id123"});

        verifyAll();

        assertEquals(ribbonRequestMock, ribbonRequest);
    }

    @Test
    public void testPostWithDomainObjectAndTransformer() throws Exception {
        doTestPostWith("/movies", "registerMovie", new Movie());
    }

    @Test
    public void testPostWithString() throws Exception {
        doTestPostWith("/titles", "registerTitle", "some title");
    }

    @Test
    public void testPostWithByteBuf() throws Exception {
        doTestPostWith("/binaries/byteBuf", "registerByteBufBinary", createMock(ByteBuf.class));
    }

    @Test
    public void testPostWithByteArray() throws Exception {
        doTestPostWith("/binaries/byteArray", "registerByteArrayBinary", new byte[]{1});
    }

    private void doTestPostWith(String uriTemplate, String methodName, Object contentObject) {
        expectUrlBase("POST", uriTemplate);

        expect(httpResourceGroupMock.newTemplateBuilder(methodName)).andReturn(httpRequestTemplateBuilderMock);
        expect(httpRequestTemplateBuilderMock.withRequestCacheKey(methodName)).andReturn(httpRequestTemplateBuilderMock);
        expect(httpRequestTemplateBuilderMock.withFallbackProvider(anyObject(MovieFallbackHandler.class))).andReturn(httpRequestTemplateBuilderMock);
        expect(requestBuilderMock.withRawContentSource(anyObject(Observable.class), anyObject(ContentTransformer.class))).andReturn(requestBuilderMock);

        replayAll();

        MethodTemplateExecutor executor = createExecutor(SampleMovieService.class, methodName);
        RibbonRequest ribbonRequest = executor.executeFromTemplate(new Object[]{contentObject});

        verifyAll();

        assertEquals(ribbonRequestMock, ribbonRequest);
    }

    @Test
    public void testFromFactory() throws Exception {
        expect(httpResourceGroupMock.newTemplateBuilder(anyObject(String.class))).andReturn(httpRequestTemplateBuilderMock).anyTimes();
        expect(httpRequestTemplateBuilderMock.withMethod(anyObject(String.class))).andReturn(httpRequestTemplateBuilderMock).anyTimes();
        expect(httpRequestTemplateBuilderMock.withUriTemplate(anyObject(String.class))).andReturn(httpRequestTemplateBuilderMock).anyTimes();
        replayAll();

        Map<Method, MethodTemplateExecutor> executorMap = MethodTemplateExecutor.from(httpResourceGroupMock, ShortMovieService.class, AnnotationProcessorsProvider.DEFAULT);

        assertEquals(ShortMovieService.class.getMethods().length, executorMap.size());
    }

    private void expectUrlBase(String method, String path) {
        expect(httpRequestTemplateBuilderMock.withMethod(method)).andReturn(httpRequestTemplateBuilderMock);
        expect(httpRequestTemplateBuilderMock.withUriTemplate(path)).andReturn(httpRequestTemplateBuilderMock);
    }

    private MethodTemplateExecutor createExecutor(Class<?> clientInterface, String methodName) {
        MethodTemplate methodTemplate = new MethodTemplate(methodByName(clientInterface, methodName));
        return new MethodTemplateExecutor(httpResourceGroupMock, methodTemplate, AnnotationProcessorsProvider.DEFAULT);
    }
}
