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

import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static junit.framework.Assert.*;

/**
 * @author Tomasz Bak
 */
public class UtilsTest {

    @Test
    public void testMethodByName() throws Exception {
        Method source = Utils.methodByName(String.class, "equals");
        assertNotNull(source);
        assertEquals("equals", source.getName());

        assertNull(Utils.methodByName(String.class, "not_equals"));
    }

    @Test
    public void testExecuteOnInstance() throws Exception {
        Method source = Utils.methodByName(String.class, "equals");
        Object obj = new Object();
        assertEquals(Boolean.TRUE, Utils.executeOnInstance(obj, source, new Object[]{obj}));
        assertEquals(Boolean.FALSE, Utils.executeOnInstance(obj, source, new Object[]{this}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecuteNotExistingMethod() throws Exception {
        Method source = Utils.methodByName(String.class, "getChars");
        Utils.executeOnInstance(new Object(), source, new Object[]{});
    }

    @Test
    public void testNewInstance() throws Exception {
        assertNotNull(Utils.newInstance(Object.class));
    }

    @Test(expected = RibbonProxyException.class)
    public void testNewInstanceForFailure() throws Exception {
        Utils.newInstance(InputStream.class);
    }
}