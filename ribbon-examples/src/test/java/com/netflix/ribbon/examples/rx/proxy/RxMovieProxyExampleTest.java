package com.netflix.ribbon.examples.rx.proxy;

import com.netflix.ribbon.examples.rx.RxMovieClientTestBase;
import org.junit.Test;

import static junit.framework.Assert.*;

/**
 * @author Tomasz Bak
 */
public class RxMovieProxyExampleTest extends RxMovieClientTestBase {

    @Test
    public void testProxyExample() throws Exception {
        assertTrue(new RxMovieProxyExample(port).runExample());
    }
}