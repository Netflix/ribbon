package com.netflix.ribbon.proxy.sample;

import com.netflix.ribbon.http.HttpResourceGroup;

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
