package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.ResourceGroup;
import com.netflix.ribbonclientextensions.http.HttpResourceGroup;

import static java.lang.String.*;

/**
 * @author Tomasz Bak
 */
public class HttpResourceGroupFactory {
    private final ClassTemplate classTemplate;

    public HttpResourceGroupFactory(ClassTemplate classTemplate) {
        this.classTemplate = classTemplate;
    }

    public HttpResourceGroup createResourceGroup() {
        String name = classTemplate.getResourceGroupName();
        Class<? extends ResourceGroup<?>> resourceClass = classTemplate.getResourceGroupClass();
        if (name != null) {
            return new HttpResourceGroup(name);
        }
        if (resourceClass == null) {
            throw new RibbonTypedClientException(format(
                    "ResourceGroup not defined for interface %s - must be provided by annotation or passed explicitly during dynamic proxy creation",
                    classTemplate.getClientInterface()));
        }
        try {
            return (HttpResourceGroup) resourceClass.newInstance();
        } catch (Exception e) {
            throw new RibbonTypedClientException("Failure of ResourceGroup instantiation from class " + resourceClass, e);
        }
    }
}
