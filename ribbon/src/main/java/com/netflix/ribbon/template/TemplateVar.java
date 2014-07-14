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
package com.netflix.ribbon.template;


/**
 * TemplateVar is a base type for use in the template parser &amp; URI Fragment builder to isolate template values from
 * static values
 */
class TemplateVar {
    private final String val;

    TemplateVar(String val) {
        this.val = val;
    }

    public String toString() {
        return val;
    }
}