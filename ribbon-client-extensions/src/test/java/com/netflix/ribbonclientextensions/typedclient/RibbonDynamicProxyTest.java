package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.http.HttpResourceGroup;
import com.netflix.ribbonclientextensions.typedclient.sample.Movie;
import com.netflix.ribbonclientextensions.typedclient.sample.MovieServiceInterfaces.SampleMovieService;
import com.netflix.ribbonclientextensions.typedclient.sample.MovieServiceInterfaces.SampleMovieServiceWithResourceGroupNameAnnotation;
import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.ribbonclientextensions.typedclient.ReflectUtil.*;
import static org.easymock.EasyMock.*;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.*;
import static org.powermock.api.easymock.PowerMock.replay;
import static org.testng.Assert.*;

/**
 * @author Tomasz Bak
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({RibbonDynamicProxy.class, MethodTemplateExecutor.class})
public class RibbonDynamicProxyTest {

    @Test(expected = IllegalArgumentException.class)
    public void testAcceptsInterfaceOnly() throws Exception {
        RibbonDynamicProxy.newInstance(Object.class, null);
    }

    @Test
    public void testSetupWithExplicitResourceGroupObject() throws Exception {
        HttpResourceGroup httpResourceGroupMock = createMock(HttpResourceGroup.class);

        replay(httpResourceGroupMock);

        RibbonDynamicProxy.newInstance(SampleMovieServiceWithResourceGroupNameAnnotation.class, httpResourceGroupMock);
    }

    @Test
    public void testSetupWithResourceGroupNameInAnnotation() throws Exception {
        HttpResourceGroup httpResourceGroupMock = createMock(HttpResourceGroup.class);

        HttpResourceGroupFactory httpResourceGroupFactoryMock = createMock(HttpResourceGroupFactory.class);
        expect(httpResourceGroupFactoryMock.createResourceGroup()).andReturn(httpResourceGroupMock);

        mockStatic(HttpResourceGroupFactory.class);
        expectNew(HttpResourceGroupFactory.class, new Class[]{ClassTemplate.class}, EasyMock.anyObject()).andReturn(httpResourceGroupFactoryMock);

        replay(HttpResourceGroupFactory.class, httpResourceGroupMock, httpResourceGroupFactoryMock);

        RibbonDynamicProxy.newInstance(SampleMovieServiceWithResourceGroupNameAnnotation.class, null);
    }

    @Test
    public void testTypedClientGetWithPathParameter() throws Exception {
        HttpResourceGroup httpResourceGroup = createMock(HttpResourceGroup.class);

        RibbonRequest ribbonRequestMock = createMock(RibbonRequest.class);

        MethodTemplateExecutor tgMock = createMock(MethodTemplateExecutor.class);
        expect(tgMock.executeFromTemplate((com.netflix.ribbonclientextensions.http.HttpResourceGroup) EasyMock.anyObject(), (Object[]) EasyMock.anyObject())).andReturn(ribbonRequestMock);

        Map<Method, MethodTemplateExecutor<Object>> tgMap = new HashMap<Method, MethodTemplateExecutor<Object>>();
        tgMap.put(methodByName(SampleMovieService.class, "findMovieById"), tgMock);

        mockStatic(MethodTemplateExecutor.class);
        expect(MethodTemplateExecutor.from(SampleMovieService.class)).andReturn(tgMap);

        replay(MethodTemplateExecutor.class, tgMock, httpResourceGroup, ribbonRequestMock);

        SampleMovieService service = RibbonDynamicProxy.newInstance(SampleMovieService.class, httpResourceGroup);
        RibbonRequest<Movie> ribbonMovie = service.findMovieById("123");

        assertNotNull(ribbonMovie);
    }

    @Test
    public void testPlainObjectInvocations() throws Exception {
        HttpResourceGroup httpResourceGroupMock = createMock(HttpResourceGroup.class);
        replay(httpResourceGroupMock);

        SampleMovieService service = RibbonDynamicProxy.newInstance(SampleMovieService.class, httpResourceGroupMock);

        assertFalse(service.equals(this));
        assertEquals(service.toString(), "RibbonDynamicProxy{...}");
    }
}
