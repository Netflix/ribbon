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

import java.util.List;

public class ParsedTemplate {

    private List<Object> parsed;
    private String template;

    public ParsedTemplate(List<Object> parsed, String template) {
        super();
        this.parsed = parsed;
        this.template = template;
    }

    public final List<Object> getParsed() {
        return parsed;
    }
    
    public final String getTemplate() {
        return template;
    }
    
    public static ParsedTemplate create(String template) {
        List<Object> parsed = TemplateParser.parseTemplate(template);
        return new ParsedTemplate(parsed, template);
    }
}
