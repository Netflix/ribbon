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
package com.netflix.niws.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.netflix.niws.cert.AbstractSslContextFactory;

/**
 * Secure socket factory that is used the NIWS code if a non-standard key store or trust store
 * is specified.
 *
 * @author Danny Yuan
 * @author Peter D. Stout
 */
public class URLSslContextFactory extends AbstractSslContextFactory{
    private final static Logger LOGGER = LoggerFactory.getLogger(URLSslContextFactory.class);

    
    private final URL keyStoreUrl;
    private final URL trustStoreUrl;
    

    /**
     * Creates a {@code ClientSSLSocketFactory} instance. This instance loads only the given trust
     * store file and key store file. Both trust store and key store must be protected by passwords,
     * even though it is not mandated by JSSE.
     *
     * @param trustStoreUrl A {@link URL} that points to a trust store file. If non-null, this URL
     *        must refer to a JKS key store file that contains trusted certificates.
     * @param trustStorePassword The password of the given trust store file. If a trust store is
     *        specified, then the password may not be empty.
     * @param keyStoreUrl A {@code URL} that points to a key store file that contains both client
     *        certificate and the client's private key. If non-null, this URL must be of JKS format.
     * @param keyStorePassword the password of the given key store file. If a key store is
     *        specified, then the password may not be empty.
     * @throws ClientSslSocketFactoryException thrown if creating this instance fails.
     */
    public URLSslContextFactory(final URL trustStoreUrl, final String trustStorePassword, final URL keyStoreUrl, final String keyStorePassword) throws ClientSslSocketFactoryException {
    	super(createKeyStore(trustStoreUrl, trustStorePassword), trustStorePassword, createKeyStore(keyStoreUrl, keyStorePassword), keyStorePassword);

    	this.keyStoreUrl = keyStoreUrl;
    	this.trustStoreUrl = trustStoreUrl;

    	LOGGER.info("Loaded keyStore from: {}", keyStoreUrl);
    	LOGGER.info("loaded trustStore from: {}", trustStoreUrl);
    }



    /**
     * Opens the specified key or trust store using the given password.
     *
     * @param storeFile the location of the store to load
     * @param password the password protecting the store
     * @return the newly loaded key store
     * @throws ClientSslSocketFactoryException 
     * @throws KeyStoreException if the JRE doesn't support the standard Java Keystore format, in
     *         other words: never
     * @throws NoSuchAlgorithmException if the algorithm used to check the integrity of the keystore
     *         cannot be found
     * @throws CertificateException if any of the certificates in the keystore could not be loaded
     * @throws IOException if there is an I/O or format problem with the keystore data, if a
     *         password is required but not given, or if the given password was incorrect. If the
     *         error is due to a wrong password, the cause of the IOException should be an
     *         UnrecoverableKeyException
     */
    private static KeyStore createKeyStore(final URL storeFile, final String password) throws ClientSslSocketFactoryException {
    	
    	if(storeFile == null){
    		return null;
    	}
    	
    	Preconditions.checkArgument(StringUtils.isNotEmpty(password), "Null keystore should have empty password, defined keystore must have password");
    	
    	KeyStore keyStore = null;
    	
    	try{
    		keyStore = KeyStore.getInstance("jks");

    		InputStream is = storeFile.openStream();

    		try {
    			keyStore.load(is, password.toCharArray());
    		} catch (NoSuchAlgorithmException e) {
    			throw new ClientSslSocketFactoryException(String.format("Failed to create a keystore that supports algorithm %s: %s", SOCKET_ALGORITHM, e.getMessage()), e);
			} catch (CertificateException e) {
				throw new ClientSslSocketFactoryException(String.format("Failed to create keystore with algorithm %s due to certificate exception: %s", SOCKET_ALGORITHM, e.getMessage()), e);
			} finally {
    			try {
    				is.close();
    			} catch (IOException ignore) { // NOPMD    				
    			}
    		}
    	}catch(KeyStoreException e){
    		throw new ClientSslSocketFactoryException(String.format("KeyStore exception creating keystore: %s", e.getMessage()), e);
    	} catch (IOException e) {
    		throw new ClientSslSocketFactoryException(String.format("IO exception creating keystore: %s", e.getMessage()), e);
		}

        return keyStore;
    }


    
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append("ClientSslSocketFactory [trustStoreUrl=").append(trustStoreUrl);
        if (trustStoreUrl != null) {
            builder.append(", trustStorePassword=");
            builder.append(Strings.repeat("*", this.getTrustStorePasswordLength()));
        }
        builder.append(", keyStoreUrl=").append(keyStoreUrl);
        if (keyStoreUrl != null) {
            builder.append(", keystorePassword = ");
            builder.append(Strings.repeat("*", this.getKeyStorePasswordLength()));
        }
        builder.append(']');

        return builder.toString();
    }


}
