package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.typedclient.sample.Movie;
import com.netflix.ribbonclientextensions.typedclient.sample.SampleTypedMovieService;
import io.reactivex.netty.protocol.http.client.HttpClient;
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
    public void testTypedClientGetWithPathParameter() throws Exception {
        HttpClient httpClientMock = createMock(HttpClient.class);

        RibbonRequest ribbonRequestMock = createMock(RibbonRequest.class);

        MethodTemplateExecutor tgMock = createMock(MethodTemplateExecutor.class);
        expect(tgMock.executeFromTemplate((HttpClient) EasyMock.anyObject(), (Object[]) EasyMock.anyObject())).andReturn(ribbonRequestMock);

        Map<Method, MethodTemplateExecutor<Object>> tgMap = new HashMap<Method, MethodTemplateExecutor<Object>>();
        tgMap.put(methodByName(SampleTypedMovieService.class, "findMovieById"), tgMock);

        mockStatic(MethodTemplateExecutor.class);
        expect(MethodTemplateExecutor.from(SampleTypedMovieService.class)).andReturn(tgMap);

        replay(MethodTemplateExecutor.class, tgMock, httpClientMock, ribbonRequestMock);

        SampleTypedMovieService service = RibbonDynamicProxy.newInstance(SampleTypedMovieService.class, httpClientMock);
        RibbonRequest<Movie> ribbonMovie = service.findMovieById("123");

        assertNotNull(ribbonMovie);
    }

    @Test
    public void testPlainObjectInvocations() throws Exception {
        SampleTypedMovieService service = RibbonDynamicProxy.newInstance(SampleTypedMovieService.class, null);

        assertFalse(service.equals(this));
        assertEquals(service.toString(), "RibbonDynamicProxy{...}");
    }
}
