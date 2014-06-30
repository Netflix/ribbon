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
package com.netflix.ribbonclientextensions.proxy.annotation;

import com.netflix.ribbonclientextensions.http.HttpResourceGroup;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceGroup {
    String name() default "";

    Class<? extends HttpResourceGroup> resourceGroupClass() default UndefHttpResourceGroup.class;

    /**
     * Since null is not allowed as a default value in annotation, we need this marker class.
     */
    final class UndefHttpResourceGroup extends HttpResourceGroup {

        public UndefHttpResourceGroup() {
            super("undef");
            throw new IllegalStateException("Marker class");
        }
    }
}
