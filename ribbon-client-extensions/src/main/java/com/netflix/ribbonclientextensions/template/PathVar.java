package com.netflix.ribbonclientextensions.template;


/**
 * PathVar is used to represent a variable path portion of a URI template.  eg {var} in /template/{var}
 */
public  class PathVar extends TemplateVar {
    PathVar(String val) {
        super(val);
    }
}