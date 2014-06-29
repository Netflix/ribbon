package com.netflix.ribbonclientextensions.proxy;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.http.HttpResourceGroup;
import com.netflix.ribbonclientextensions.proxy.sample.Movie;
import com.netflix.ribbonclientextensions.proxy.sample.MovieServiceInterfaces.SampleMovieService;
import com.netflix.ribbonclientextensions.proxy.sample.MovieServiceInterfaces.SampleMovieServiceWithResourceGroupNameAnnotation;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.ribbonclientextensions.proxy.Utils.*;
import static org.easymock.EasyMock.*;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.*;
import static org.testng.Assert.*;

/**
 * @author Tomasz Bak
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({RibbonDynamicProxy.class, MethodTemplateExecutor.class})
public class RibbonDynamicProxyTest {

    private HttpResourceGroup httpResourceGroupMock = createMock(HttpResourceGroup.class);

    private HttpResourceGroupFactory httpResourceGroupFactoryMock = createMock(HttpResourceGroupFactory.class);

    private RibbonRequest ribbonRequestMock = createMock(RibbonRequest.class);

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
        mockStatic(HttpResourceGroupFactory.class);
        expectNew(HttpResourceGroupFactory.class, new Class[]{ClassTemplate.class}, anyObject()).andReturn(httpResourceGroupFactoryMock);

        replayAll();

        RibbonDynamicProxy.newInstance(SampleMovieServiceWithResourceGroupNameAnnotation.class, null);
    }

    @Test
    public void testTypedClientGetWithPathParameter() throws Exception {
        initializeSampleMovieServiceMocks();
        replayAll();

        SampleMovieService service = RibbonDynamicProxy.newInstance(SampleMovieService.class, httpResourceGroupMock);
        RibbonRequest<Movie> ribbonMovie = service.findMovieById("123");

        assertNotNull(ribbonMovie);
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
