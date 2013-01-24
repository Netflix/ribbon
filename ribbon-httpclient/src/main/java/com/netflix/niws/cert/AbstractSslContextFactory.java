/*
*
* Copyright 2013 Netflix, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
package com.netflix.niws.cert;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.niws.client.ClientSslSocketFactoryException;

/**
 * 
 * Abstract class to represent what we logically associate with the ssl context on the client side,
 * namely, the keystore and truststore.
 * 
 * @author jzarfoss
 *
 */
public abstract class AbstractSslContextFactory {

	
    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractSslContextFactory.class);
	
    /** The secure socket algorithm that is to be used. */
    public static final String SOCKET_ALGORITHM = "SSL";

    /** The keystore resulting from loading keystore URL     */
    private KeyStore keyStore;
    
    /** The truststore resulting from loading the truststore URL     */
    private KeyStore trustStore;
    
    /** The password for the keystore     */
    private String keyStorePassword;
    
    private final int trustStorePasswordLength;
    
    private final int keyStorePasswordLength;
	
	
    protected AbstractSslContextFactory(final KeyStore trustStore, final String trustStorePassword, final KeyStore keyStore, final String keyStorePassword){
    	
    	this.trustStore = trustStore;
    	this.keyStorePassword = keyStorePassword;
    	this.keyStore = keyStore;
    	
    	this.keyStorePasswordLength = keyStorePassword != null ? keyStorePassword.length() : -1;
    	this.trustStorePasswordLength = trustStorePassword != null ? trustStorePassword.length() : -1;
    }
    
    public KeyStore getKeyStore(){
    	return this.keyStore;
    }
    
    public KeyStore getTrustStore(){
    	return this.trustStore;
    }
    
    public int getKeyStorePasswordLength(){
    	return this.keyStorePasswordLength;
    }
    
    public int getTrustStorePasswordLength(){
    	return this.trustStorePasswordLength;
    }
    
    
    /**
     * Creates the SSL context needed to create the socket factory used by this factory. The key and
     * trust store parameters are optional. If they are null then the JRE defaults will be used.
     *
     * @return the newly created SSL context
     * @throws ClientSslSocketFactoryException if an error is detected loading the specified key or
     *         trust stores
     */
    private SSLContext createSSLContext() throws ClientSslSocketFactoryException {
        final KeyManager[] keyManagers = this.keyStore != null ? createKeyManagers() : null;
        final TrustManager[] trustManagers = this.trustStore != null ? createTrustManagers() : null;

        try {
            final SSLContext sslcontext = SSLContext.getInstance(SOCKET_ALGORITHM);

            sslcontext.init(keyManagers, trustManagers, null);

            return sslcontext;
        } catch (NoSuchAlgorithmException e) {
            throw new ClientSslSocketFactoryException(String.format("Failed to create an SSL context that supports algorithm %s: %s", SOCKET_ALGORITHM, e.getMessage()), e);
        } catch (KeyManagementException e) {
            throw new ClientSslSocketFactoryException(String.format("Failed to initialize an SSL context: %s", e.getMessage()), e);
        }
    }
    
    
    /**
     * Creates the key managers to be used by the factory from the associated key store and password.
     *
     * @return the newly created array of key managers
     * @throws ClientSslSocketFactoryException if an exception is detected in loading the key store
     */
    private KeyManager[] createKeyManagers() throws ClientSslSocketFactoryException {

        final KeyManagerFactory factory;

        try {
            factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(this.keyStore, this.keyStorePassword.toCharArray());
        } catch (NoSuchAlgorithmException e) {
            throw new ClientSslSocketFactoryException(
                    String.format("Failed to create the key store because the algorithm %s is not supported. ",
                            KeyManagerFactory.getDefaultAlgorithm()), e);
        } catch (UnrecoverableKeyException e) {
        	throw new ClientSslSocketFactoryException("Unrecoverable Key Exception initializing key manager factory; this is probably fatal", e);
		} catch (KeyStoreException e) {
			throw new ClientSslSocketFactoryException("KeyStore exception initializing key manager factory; this is probably fatal", e);
		}

        KeyManager[] managers = factory.getKeyManagers();

        LOGGER.debug("Key managers are initialized. Total {} managers. ", managers.length);
        
        return managers;
    }

    /**
     * Creates the trust managers to be used by the factory from the specified trust store file and
     * password.
     *
     * @return the newly created array of trust managers
     * @throws ClientSslSocketFactoryException if an error is detected in loading the trust store
     */
    private TrustManager[] createTrustManagers() throws ClientSslSocketFactoryException {

    	final TrustManagerFactory factory;

    	try {
    		factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    		factory.init(this.trustStore);
    	} catch (NoSuchAlgorithmException e) {
    		throw new ClientSslSocketFactoryException(String.format("Failed to create the trust store because the algorithm %s is not supported. ",
    				KeyManagerFactory.getDefaultAlgorithm()), e);
    	} catch (KeyStoreException e) {
    		throw new ClientSslSocketFactoryException("KeyStore exception initializing trust manager factory; this is probably fatal", e);
    	}

    	final TrustManager[] managers = factory.getTrustManagers();

    	LOGGER.debug("TrustManagers are initialized. Total {} managers: ", managers.length);

    	return managers;

    }

    public SSLContext getSSLContext() throws ClientSslSocketFactoryException{
    	return createSSLContext(); 
    }
    
    
    

	
}
