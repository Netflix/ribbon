package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.http.HttpResourceGroup;
import com.netflix.ribbonclientextensions.typedclient.annotation.ResourceGroupSpec;

import static java.lang.String.*;

/**
 * @author Tomasz Bak
 */
public class ClassTemplate<T> {
    private final Class<T> clientInterface;
    private final String resourceGroupName;
    private final Class<? extends HttpResourceGroup> resourceGroupClass;

    public ClassTemplate(Class<T> clientInterface) {
        this.clientInterface = clientInterface;

        ResourceGroupSpec annotation = clientInterface.getAnnotation(ResourceGroupSpec.class);
        if (annotation != null) {
            String name = annotation.name().trim();
            resourceGroupName = name.equals("") ? null : annotation.name();
            Class resourceClass = annotation.resourceGroupClass();
            resourceGroupClass = void.class.isAssignableFrom(resourceClass) ? null : resourceClass;
            verify();
        } else {
            resourceGroupName = null;
            resourceGroupClass = null;
        }
    }

    public Class<T> getClientInterface() {
        return clientInterface;
    }

    public String getResourceGroupName() {
        return resourceGroupName;
    }

    public Class<? extends HttpResourceGroup> getResourceGroupClass() {
        return resourceGroupClass;
    }

    public static ClassTemplate from(Class clientInterface) {
        return new ClassTemplate(clientInterface);
    }

    private void verify() {
        if (resourceGroupName != null && resourceGroupClass != null) {
            throw new RibbonTypedClientException("Both resource group name and class defined with @ResourceGroupSpec");
        }
        if (resourceGroupClass != null && !HttpResourceGroup.class.isAssignableFrom(resourceGroupClass)) {
            throw new RibbonTypedClientException(format("Class %s does not extend %s", resourceGroupClass, HttpResourceGroup.class));
        }
    }
}
