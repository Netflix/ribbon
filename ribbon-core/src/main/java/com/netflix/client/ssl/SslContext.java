package com.netflix.client.ssl;

import javax.net.ssl.SSLContext;

public interface SslContext {

    /** The secure socket algorithm that is to be used. */
    String SOCKET_ALGORITHM = "SSL";

    SSLContext getSSLContext() throws ClientSslContextFactoryException;
}
