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
package com.netflix.ribbon.proxy;

import java.lang.reflect.Method;

import static java.lang.String.*;

/**
 * A collection of helper methods.
 *
 * @author Tomasz Bak
 */
public final class Utils {
    private Utils() {
    }

    public static <T> Method methodByName(Class<T> aClass, String name) {
        for (Method m : aClass.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    public static Object executeOnInstance(Object object, Method method, Object[] args) {
        Method targetMethod = methodByName(object.getClass(), method.getName());
        if (targetMethod == null) {
            throw new IllegalArgumentException(format(
                    "Signature of method %s is not compatible with the object %s",
                    method.getName(), object.getClass().getSimpleName()));
        }
        try {
            return targetMethod.invoke(object, args);
        } catch (Exception ex) {
            throw new RibbonProxyException(format(
                    "Failed to execute method %s on object %s",
                    method.getName(), object.getClass().getSimpleName()), ex);
        }
    }

    public static <T> T newInstance(Class<T> aClass) {
        try {
            return aClass.newInstance();
        } catch (Exception e) {
            throw new RibbonProxyException("Cannot instantiate object from class " + aClass, e);
        }
    }
}
