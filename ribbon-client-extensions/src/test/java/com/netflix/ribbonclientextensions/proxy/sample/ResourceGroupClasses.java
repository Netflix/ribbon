package com.netflix.ribbonclientextensions.proxy.sample;

import com.netflix.ribbonclientextensions.http.HttpResourceGroup;

/**
 * @author Tomasz Bak
 */
public class ResourceGroupClasses {
    public static class SampleHttpResourceGroup extends HttpResourceGroup {
        public SampleHttpResourceGroup() {
            super("myTestGroup");
        }
    }
}
