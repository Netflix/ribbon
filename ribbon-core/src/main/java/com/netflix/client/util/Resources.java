package com.netflix.client.util;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Resources {
    private static final Logger logger = LoggerFactory.getLogger(Resources.class);
    
    public static URL getResource(String resourceName) {
        URL url = null;
        // attempt to load from the context classpath
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            url = loader.getResource(resourceName);
        }
        if (url == null) {
            // attempt to load from the system classpath
            url = ClassLoader.getSystemResource(resourceName);
        }
        if (url == null) {
            try {
                resourceName = URLDecoder.decode(resourceName, "UTF-8");
                url = (new File(resourceName)).toURI().toURL();
            } catch (Exception e) {
                logger.error("Problem loading resource", e);
            }
        }
        return url;
    }
}
