package com.netflix.client;

public class UnexpectedResponseException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public UnexpectedResponseException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public UnexpectedResponseException(String arg0) {
        super(arg0);
    }

    public UnexpectedResponseException(Throwable arg0) {
        super(arg0);
    }
}
