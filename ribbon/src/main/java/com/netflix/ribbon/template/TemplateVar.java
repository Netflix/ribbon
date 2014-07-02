package com.netflix.ribbon.template;


/**
 * TemplateVar is a base type for use in the template parser & URI Fragment builder to isolate template values from
 * static values
 */
public  class TemplateVar {
    private final String val;

    TemplateVar(String val) {
        this.val = val;
    }

    public String toString() {
        return val;
    }
}