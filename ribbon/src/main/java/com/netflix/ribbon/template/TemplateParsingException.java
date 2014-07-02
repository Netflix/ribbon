package com.netflix.ribbon.template;

public class TemplateParsingException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1910187667077051723L;

    public TemplateParsingException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public TemplateParsingException(String arg0) {
        super(arg0);
    }
    

}
