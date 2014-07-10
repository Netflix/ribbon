package com.netflix.ribbon.examples.rx.transport;

import com.netflix.ribbon.examples.rx.RxMovieClientTestBase;
import org.junit.Test;

import static junit.framework.Assert.*;

/**
 * @author Tomasz Bak
 */
public class RxMovieTransportExampleTest extends RxMovieClientTestBase {

    @Test
    public void testTemplateExample() throws Exception {
        assertTrue(new RxMovieTransportExample(port).runExample());
    }
}