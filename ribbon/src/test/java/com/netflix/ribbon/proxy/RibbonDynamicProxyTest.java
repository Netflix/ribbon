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

import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider;
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

import static com.netflix.ribbon.proxy.Utils.methodByName;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.*;
import static org.junit.Assert.*;

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
        expectNew(ProxyHttpResourceGroupFactory.class, new Class[]{ClassTemplate.class, 
            RibbonResourceFactory.class, AnnotationProcessorsProvider.class
            }, anyObject(), anyObject(), anyObject()).andReturn(httpResourceGroupFactoryMock);

        replayAll();

        RibbonDynamicProxy.newInstance(SampleMovieServiceWithResourceGroupNameAnnotation.class);
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
        expect(MethodTemplateExecutor.from(httpResourceGroupMock, SampleMovieService.class, AnnotationProcessorsProvider.DEFAULT)).andReturn(tgMap);
    }
}
