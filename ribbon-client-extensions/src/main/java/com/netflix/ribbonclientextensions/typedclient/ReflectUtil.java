package com.netflix.ribbonclientextensions.typedclient;

import java.lang.reflect.Method;

import static java.lang.String.*;

/**
 * A collection of reflection helper methods.
 *
 * @author Tomasz Bak
 */
class ReflectUtil {
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
            throw new RuntimeException(format(
                    "Failed to execute method %s on object %s",
                    method.getName(), object.getClass().getSimpleName()), ex);
        }
    }
}
