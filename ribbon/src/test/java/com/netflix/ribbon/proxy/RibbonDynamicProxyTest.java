package com.netflix.ribbon.proxy;

import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.ClassTemplate;
import com.netflix.ribbon.proxy.ProxyHttpResourceGroupFactory;
import com.netflix.ribbon.proxy.MethodTemplateExecutor;
import com.netflix.ribbon.proxy.ProxyLifeCycle;
import com.netflix.ribbon.proxy.RibbonDynamicProxy;
import com.netflix.ribbon.proxy.sample.Movie;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieService;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieServiceWithResourceGroupNameAnnotation;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.ribbon.proxy.Utils.*;
import static org.easymock.EasyMock.*;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.expectLastCall;
import static org.powermock.api.easymock.PowerMock.*;
import static org.testng.Assert.*;

/**
 * @author Tomasz Bak
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({RibbonDynamicProxy.class, MethodTemplateExecutor.class})
public class RibbonDynamicProxyTest {

    @Mock
    private HttpResourceGroup httpResourceGroupMock;

    @Mock
    private ProxyHttpResourceGroupFactory httpResourceGroupFactoryMock;

    @Mock
    private RibbonRequest ribbonRequestMock;

    @Mock
    private HttpClient<ByteBuf, ByteBuf> httpClientMock;

    @Before
    public void setUp() throws Exception {
        expect(httpResourceGroupFactoryMock.createResourceGroup()).andReturn(httpResourceGroupMock);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAcceptsInterfaceOnly() throws Exception {
        RibbonDynamicProxy.newInstance(Object.class, null);
    }

    @Test
    public void testSetupWithExplicitResourceGroupObject() throws Exception {
        replayAll();

        RibbonDynamicProxy.newInstance(SampleMovieServiceWithResourceGroupNameAnnotation.class, httpResourceGroupMock);
    }

    @Test
    public void testSetupWithResourceGroupNameInAnnotation() throws Exception {
        mockStatic(ProxyHttpResourceGroupFactory.class);
        expectNew(ProxyHttpResourceGroupFactory.class, new Class[]{ClassTemplate.class}, anyObject()).andReturn(httpResourceGroupFactoryMock);

        replayAll();

        RibbonDynamicProxy.newInstance(SampleMovieServiceWithResourceGroupNameAnnotation.class, null);
    }

    @Test
    public void testTypedClientGetWithPathParameter() throws Exception {
        initializeSampleMovieServiceMocks();
        replayAll();

        SampleMovieService service = RibbonDynamicProxy.newInstance(SampleMovieService.class, httpResourceGroupMock);
        RibbonRequest<ByteBuf> ribbonMovie = service.findMovieById("123");

        assertNotNull(ribbonMovie);
    }

    @Test
    public void testLifeCycleShutdown() throws Exception {
        initializeSampleMovieServiceMocks();
        expect(httpResourceGroupMock.getClient()).andReturn(httpClientMock);
        httpClientMock.shutdown();
        expectLastCall();
        replayAll();

        SampleMovieService service = RibbonDynamicProxy.newInstance(SampleMovieService.class, httpResourceGroupMock);
        ProxyLifeCycle proxyLifeCycle = (ProxyLifeCycle) service;
        proxyLifeCycle.shutdown();

        assertTrue(proxyLifeCycle.isShutDown());
    }

    @Test
    public void testPlainObjectInvocations() throws Exception {
        initializeSampleMovieServiceMocks();
        replayAll();

        SampleMovieService service = RibbonDynamicProxy.newInstance(SampleMovieService.class, httpResourceGroupMock);

        assertFalse(service.equals(this));
        assertEquals(service.toString(), "RibbonDynamicProxy{...}");
    }

    private void initializeSampleMovieServiceMocks() {
        MethodTemplateExecutor tgMock = createMock(MethodTemplateExecutor.class);
        expect(tgMock.executeFromTemplate(anyObject(Object[].class))).andReturn(ribbonRequestMock);

        Map<Method, MethodTemplateExecutor> tgMap = new HashMap<Method, MethodTemplateExecutor>();
        tgMap.put(methodByName(SampleMovieService.class, "findMovieById"), tgMock);

        mockStatic(MethodTemplateExecutor.class);
        expect(MethodTemplateExecutor.from(httpResourceGroupMock, SampleMovieService.class)).andReturn(tgMap);
    }
}
