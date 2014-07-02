/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbonclientextensions.proxy;

import com.netflix.ribbonclientextensions.http.HttpResourceGroup;

import static java.lang.String.*;

/**
 * @author Tomasz Bak
 */
class HttpResourceGroupFactory<T> {
    private final ClassTemplate<T> classTemplate;

    HttpResourceGroupFactory(ClassTemplate<T> classTemplate) {
        this.classTemplate = classTemplate;
    }

    public HttpResourceGroup createResourceGroup() {
        String name = classTemplate.getResourceGroupName();
        Class<? extends HttpResourceGroup> resourceClass = classTemplate.getResourceGroupClass();
        if (name != null) {
            return new HttpResourceGroup(name);
        }
        if (resourceClass == null) {
            throw new RibbonProxyException(format(
                    "ResourceGroup not defined for interface %s - must be provided by annotation or passed explicitly during dynamic proxy creation",
                    classTemplate.getClientInterface()));
        }
        return Utils.newInstance(resourceClass);
    }
}
