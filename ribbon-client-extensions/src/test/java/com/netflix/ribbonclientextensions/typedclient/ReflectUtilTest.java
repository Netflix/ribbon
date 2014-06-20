package com.netflix.ribbonclientextensions.typedclient;

import org.junit.Test;

import java.lang.reflect.Method;

import static junit.framework.Assert.*;

/**
 * @author Tomasz Bak
 */
public class ReflectUtilTest {

    @Test
    public void testMethodByName() throws Exception {
        Method source = ReflectUtil.methodByName(String.class, "equals");
        assertNotNull(source);
        assertEquals("equals", source.getName());

        assertNull(ReflectUtil.methodByName(String.class, "not_equals"));
    }

    @Test
    public void testExecuteOnInstance() throws Exception {
        Method source = ReflectUtil.methodByName(String.class, "equals");
        Object obj = new Object();
        assertEquals(Boolean.TRUE, ReflectUtil.executeOnInstance(obj, source, new Object[]{obj}));
        assertEquals(Boolean.FALSE, ReflectUtil.executeOnInstance(obj, source, new Object[]{this}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecuteNotExistingMethod() throws Exception {
        Method source = ReflectUtil.methodByName(String.class, "getChars");
        ReflectUtil.executeOnInstance(new Object(), source, new Object[]{});
    }
}