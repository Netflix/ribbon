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
package com.netflix.http4.ssl;


import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;

import com.netflix.niws.cert.AbstractSslContextFactory;
import com.netflix.niws.client.ClientSslSocketFactoryException;

/**
 *
 * SocketFactory that remembers what keystore and truststore being used,
 * allowing for that information to be queried later.
 *
 * @author jzarfoss
 *
 */
public class KeyStoreAwareSocketFactory extends SSLSocketFactory{


	private final KeyStore keyStore;
	private final KeyStore trustStore;


	public KeyStoreAwareSocketFactory(X509HostnameVerifier hostnameVerifier) throws NoSuchAlgorithmException, KeyStoreException{
		super(SSLContext.getDefault(), hostnameVerifier);

		this.keyStore = null;
		this.trustStore = null;
	}



	public KeyStoreAwareSocketFactory(final AbstractSslContextFactory abstractFactory) throws ClientSslSocketFactoryException, NoSuchAlgorithmException{
		super(abstractFactory == null ? SSLContext.getDefault() : abstractFactory.getSSLContext());

		if(abstractFactory == null){
			this.keyStore = null;
			this.trustStore = null;
		}else{
			this.keyStore = abstractFactory.getKeyStore();
			this.trustStore = abstractFactory.getTrustStore();
		}
	}


	public KeyStoreAwareSocketFactory(final AbstractSslContextFactory abstractFactory, X509HostnameVerifier hostnameVerifier) throws ClientSslSocketFactoryException, NoSuchAlgorithmException{
		super(abstractFactory == null ? SSLContext.getDefault() : abstractFactory.getSSLContext(), hostnameVerifier);

		if(abstractFactory == null){
			this.keyStore = null;
			this.trustStore = null;
		}else{
			this.keyStore = abstractFactory.getKeyStore();
			this.trustStore = abstractFactory.getTrustStore();
		}
	}

	public KeyStore getKeyStore(){
		return this.keyStore;
	}

	public KeyStore getTrustStore(){
		return this.trustStore;
	}



}