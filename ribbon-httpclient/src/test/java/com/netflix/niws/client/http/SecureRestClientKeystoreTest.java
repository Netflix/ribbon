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
package com.netflix.niws.client.http;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;

import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.Test;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.config.ConfigurationManager;
import com.sun.jersey.core.util.Base64;

/**
 * Test keystore info is configurable/retrievable
 *
 * @author jzarfoss
 *
 */
public class SecureRestClientKeystoreTest {


	@Test
	public void testGetKeystoreWithClientAuth() throws Exception{

		// jks format
		byte[] dummyTruststore = Base64.decode(SecureGetTest.TEST_TS1);
		byte[] dummyKeystore = Base64.decode(SecureGetTest.TEST_KS1);

		File tempKeystore = File.createTempFile(this.getClass().getName(), ".keystore");
		File tempTruststore = File.createTempFile(this.getClass().getName(), ".truststore");

		FileOutputStream keystoreFileOut = new FileOutputStream(tempKeystore);
		keystoreFileOut.write(dummyKeystore);
		keystoreFileOut.close();

		FileOutputStream truststoreFileOut = new FileOutputStream(tempTruststore);
		truststoreFileOut.write(dummyTruststore);
		truststoreFileOut.close();


		AbstractConfiguration cm = ConfigurationManager.getConfigInstance();

		String name = this.getClass().getName() + ".test1";

		String configPrefix = name + "." + "ribbon";

		cm.setProperty(configPrefix + "." +  CommonClientConfigKey.IsSecure, "true");
		cm.setProperty(configPrefix + "." +  CommonClientConfigKey.IsClientAuthRequired, "true");
		cm.setProperty(configPrefix + "." +  CommonClientConfigKey.KeyStore, tempKeystore.getAbsolutePath());
		cm.setProperty(configPrefix + "." +  CommonClientConfigKey.KeyStorePassword, "changeit");
		cm.setProperty(configPrefix + "." +  CommonClientConfigKey.TrustStore, tempTruststore.getAbsolutePath());
		cm.setProperty(configPrefix + "." +  CommonClientConfigKey.TrustStorePassword, "changeit");

		RestClient client = (RestClient) ClientFactory.getNamedClient(name);

		KeyStore keyStore = client.getKeyStore();

		Certificate cert = keyStore.getCertificate("ribbon_key");

		assertNotNull(cert);

	}

	@Test
	public void testGetKeystoreWithNoClientAuth() throws Exception{

		// jks format
		byte[] dummyTruststore = Base64.decode(SecureGetTest.TEST_TS1);
		byte[] dummyKeystore = Base64.decode(SecureGetTest.TEST_KS1);

		File tempKeystore = File.createTempFile(this.getClass().getName(), ".keystore");
		File tempTruststore = File.createTempFile(this.getClass().getName(), ".truststore");

		FileOutputStream keystoreFileOut = new FileOutputStream(tempKeystore);
		keystoreFileOut.write(dummyKeystore);
		keystoreFileOut.close();

		FileOutputStream truststoreFileOut = new FileOutputStream(tempTruststore);
		truststoreFileOut.write(dummyTruststore);
		truststoreFileOut.close();


		AbstractConfiguration cm = ConfigurationManager.getConfigInstance();

		String name = this.getClass().getName() + ".test2";

		String configPrefix = name + "." + "ribbon";

		cm.setProperty(configPrefix + "." +  CommonClientConfigKey.IsSecure, "true");
		cm.setProperty(configPrefix + "." +  CommonClientConfigKey.KeyStore, tempKeystore.getAbsolutePath());
		cm.setProperty(configPrefix + "." +  CommonClientConfigKey.KeyStorePassword, "changeit");

		RestClient client = (RestClient) ClientFactory.getNamedClient(name);

		KeyStore keyStore = client.getKeyStore();

		Certificate cert = keyStore.getCertificate("ribbon_key");

		assertNotNull(cert);
	}

}
