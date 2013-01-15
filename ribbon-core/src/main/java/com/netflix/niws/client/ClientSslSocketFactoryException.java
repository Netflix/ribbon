/**
 * Copyright (c) 2011 Netflix, Inc.  All rights reserved.
 */
package com.netflix.niws.client;

/**
 * Reports problems detected by the ClientSslSocketFactory class.
 *
 * @author pstout@netflix.com (Peter D. Stout)
 */
public class ClientSslSocketFactoryException extends Exception {
    /** Serial version identifier for this class. */
    private static final long serialVersionUID = 1L;

    /** Constructs a new instance with the specified message and cause. */
    public ClientSslSocketFactoryException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
