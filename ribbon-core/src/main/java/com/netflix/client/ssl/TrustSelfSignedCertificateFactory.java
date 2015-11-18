package com.netflix.client.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class TrustSelfSignedCertificateFactory implements SslContext {

    @Override
    public SSLContext getSSLContext() throws ClientSslContextFactoryException {
        return createSSLContext();
    }

    private SSLContext createSSLContext() throws ClientSslContextFactoryException {
        try {
            final SSLContext sslcontext = SSLContext.getInstance(SOCKET_ALGORITHM);
            sslcontext.init(null, new TrustSelfSignedStrategy[]{}, new SecureRandom());

            return sslcontext;
        } catch (NoSuchAlgorithmException e) {
            throw new ClientSslContextFactoryException(String.format("Failed to create an SSL context that supports algorithm %s: %s", SOCKET_ALGORITHM, e.getMessage()), e);
        } catch (KeyManagementException e) {
            throw new ClientSslContextFactoryException(String.format("Failed to initialize an SSL context: %s", e.getMessage()), e);
        }
    }

    private class TrustSelfSignedStrategy implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
