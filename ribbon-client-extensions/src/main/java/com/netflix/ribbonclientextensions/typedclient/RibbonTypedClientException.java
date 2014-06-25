package com.netflix.ribbonclientextensions.typedclient;

/**
 * @author Tomasz Bak
 */
public class RibbonTypedClientException extends RuntimeException {
    public RibbonTypedClientException(String message) {
        super(message);
    }

    public RibbonTypedClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
