package com.netflix.ribbon.template;


/**
 * MatrixVar is used to represent a matrix parameter in a URI template.  eg var in /template{;var}
 */
public  class MatrixVar extends TemplateVar {
    MatrixVar(String val) {
        super(val);
    }
}