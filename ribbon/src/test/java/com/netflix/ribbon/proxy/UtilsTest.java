package com.netflix.ribbon.proxy;

import org.junit.Test;

import com.netflix.ribbon.proxy.RibbonProxyException;
import com.netflix.ribbon.proxy.Utils;

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