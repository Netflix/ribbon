package com.netflix.ribbonclientextensions;

@SuppressWarnings("serial")
public class ServerError extends Exception {

    public ServerError(String message, Throwable cause) {
        super(message, cause);
    }

    public ServerError(String message) {
        super(message);
    }

    public ServerError(Throwable cause) {
        super(cause);
    }

}
