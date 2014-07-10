package com.netflix.ribbon.examples.rx.template;

import com.netflix.ribbon.examples.rx.RxMovieClientTestBase;
import org.junit.Test;

import static junit.framework.Assert.*;

/**
 * @author Tomasz Bak
 */
public class RxMovieTemplateExampleTest extends RxMovieClientTestBase {

    @Test
    public void testTemplateExample() throws Exception {
        assertTrue(new RxMovieTemplateExample(port).runExample());
    }

}