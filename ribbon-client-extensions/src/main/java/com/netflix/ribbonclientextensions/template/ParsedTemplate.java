package com.netflix.ribbonclientextensions.template;

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
