package com.netflix.ribbon.proxy;

import com.netflix.ribbon.CacheProvider;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.evache.EvCacheOptions;
import com.netflix.ribbon.evache.EvCacheProvider;
import com.netflix.ribbon.http.HttpRequestBuilder;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.EvCacheProviderPool;
import com.netflix.ribbon.proxy.MethodTemplate;
import com.netflix.ribbon.proxy.MethodTemplateExecutor;
import com.netflix.ribbon.proxy.sample.Movie;
import com.netflix.ribbon.proxy.sample.HystrixHandlers.MovieFallbackHandler;
import com.netflix.ribbon.proxy.sample.HystrixHandlers.SampleHttpResponseValidator;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieService;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.ShortMovieService;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.RawContentSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Method;
import java.util.Map;

import static com.netflix.ribbon.proxy.Utils.*;
import static junit.framework.Assert.*;
import static org.easymock.EasyMock.*;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.*;

/**
 * @author Tomasz Bak
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MethodTemplateExecutor.class})
public class MethodTemplateExecutorTest {

    @Mock
    private RibbonRequest ribbonRequestMock = createMock(RibbonRequest.class);

    @Mock
    private HttpRequestBuilder requestBuilderMock = createMock(HttpRequestBuilder.class);

    @Mock
    private HttpRequestTemplate httpRequestTemplateMock = createMock(HttpRequestTemplate.class);

    @Mock
    private HttpResourceGroup httpResourceGroupMock = createMock(HttpResourceGroup.class);

    @Mock
    private EvCacheProviderPool evCacheProviderPoolMock = createMock(EvCacheProviderPool.class);

    @Mock
    private EvCacheProvider evCacheProviderMock = createMock(EvCacheProvider.class);

    @Before
    public void setUp() throws Exception {
        expect(requestBuilderMock.build()).andReturn(ribbonRequestMock);
        expect(httpRequestTemplateMock.requestBuilder()).andReturn(requestBuilderMock);
    }

    @Test
    public void testGetQueryWithDomainObjectResult() throws Exception {
        expectUrlBase("GET", "/movies/{id}");

        expect(requestBuilderMock.withRequestProperty("id", "id123")).andReturn(requestBuilderMock);
        expect(httpResourceGroupMock.newRequestTemplate("findMovieById", Movie.class)).andReturn(httpRequestTemplateMock);

        expect(httpRequestTemplateMock.withHeader("X-MyHeader1", "value1.1")).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withHeader("X-MyHeader1", "value1.2")).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withHeader("X-MyHeader2", "value2")).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withRequestCacheKey("findMovieById/{id}")).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withFallbackProvider(anyObject(MovieFallbackHandler.class))).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withResponseValidator(anyObject(SampleHttpResponseValidator.class))).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.addCacheProvider(anyObject(String.class), anyObject(CacheProvider.class))).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.addCacheProvider(anyObject(String.class), anyObject(EvCacheProvider.class))).andReturn(httpRequestTemplateMock);
        expect(evCacheProviderPoolMock.getMatching(anyObject(EvCacheOptions.class))).andReturn(evCacheProviderMock);

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
        expect(httpResourceGroupMock.newRequestTemplate("findRawMovieById")).andReturn(httpRequestTemplateMock);

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
    public void testPostWithRawContent() throws Exception {
        doTestPostWith("/movies", "registerMovieRaw", createMock(RawContentSource.class));
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

        expect(requestBuilderMock.withRawContentSource(anyObject(RawContentSource.class))).andReturn(requestBuilderMock);
        expect(httpResourceGroupMock.newRequestTemplate(methodName, Void.class)).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withRequestCacheKey(methodName)).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withFallbackProvider(anyObject(MovieFallbackHandler.class))).andReturn(httpRequestTemplateMock);

        replayAll();

        MethodTemplateExecutor executor = createExecutor(SampleMovieService.class, methodName);
        RibbonRequest ribbonRequest = executor.executeFromTemplate(new Object[]{contentObject});

        verifyAll();

        assertEquals(ribbonRequestMock, ribbonRequest);
    }

    @Test
    public void testFromFactory() throws Exception {
        mockStatic(EvCacheProviderPool.class);
        expectNew(EvCacheProviderPool.class, new Class[]{MethodTemplate[].class}, anyObject(MethodTemplate[].class)).andReturn(evCacheProviderPoolMock);
        expect(evCacheProviderPoolMock.getMatching(anyObject(EvCacheOptions.class))).andReturn(evCacheProviderMock).anyTimes();

        expect(httpResourceGroupMock.newRequestTemplate(anyObject(String.class))).andReturn(httpRequestTemplateMock).anyTimes();
        expect(httpRequestTemplateMock.withMethod(anyObject(String.class))).andReturn(httpRequestTemplateMock).anyTimes();
        expect(httpRequestTemplateMock.withUriTemplate(anyObject(String.class))).andReturn(httpRequestTemplateMock).anyTimes();
        replayAll();

        Map<Method, MethodTemplateExecutor> executorMap = MethodTemplateExecutor.from(httpResourceGroupMock, ShortMovieService.class);

        assertEquals(ShortMovieService.class.getMethods().length, executorMap.size());
    }

    private void expectUrlBase(String method, String path) {
        expect(httpRequestTemplateMock.withMethod(method)).andReturn(httpRequestTemplateMock);
        expect(httpRequestTemplateMock.withUriTemplate(path)).andReturn(httpRequestTemplateMock);
    }

    private MethodTemplateExecutor createExecutor(Class<?> clientInterface, String methodName) {
        MethodTemplate methodTemplate = new MethodTemplate(methodByName(clientInterface, methodName));
        return new MethodTemplateExecutor(httpResourceGroupMock, methodTemplate, evCacheProviderPoolMock);
    }
}