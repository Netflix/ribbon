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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.Random;

import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.testutil.SimpleSSLTestServer;
import com.netflix.config.ConfigurationManager;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.core.util.Base64;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 *
 * Test TLS configurations work against a very dumbed down test server.
 *
 * @author jzarfoss
 *
 */
public class SecureGetTest {

	// custom minted test keystores/truststores for Ribbon testing

	// PLEASE DO NOT USE FOR ANYTHING OTHER THAN TESTING (the private keys are sitting right here in a String!!)
	// but if you need keystore to test with, help yourself, they're good until 2113!

	// we have a pair of independently valid keystore/truststore combinations
	// thus allowing us to perform both positive and negative testing,
	// the negative testing being that set 1 and set 2 cannot talk to each other

	// base64 encoded

	//	Keystore type: JKS
	//	Keystore provider: SUN
	//
	//	Your keystore contains 1 entry
	//
	//	Alias name: ribbon_root
	//	Creation date: Sep 6, 2013
	//	Entry type: trustedCertEntry
	//
	//	Owner: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestRoot1
	//	Issuer: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestRoot1
	//	Serial number: 1
	//	Valid from: Fri Sep 06 10:25:22 PDT 2013 until: Sun Aug 13 10:25:22 PDT 2113
	//	Certificate fingerprints:
	//		 MD5:  BD:08:2A:F3:3B:26:C0:D4:44:B9:6D:EE:D2:45:31:C0
	//		 SHA1: 64:95:7E:C6:0C:D7:81:56:28:0C:93:60:85:AB:9D:E2:60:33:70:43
	//		 Signature algorithm name: SHA1withRSA
	//		 Version: 3
	//
	//	Extensions:
	//
	//	#1: ObjectId: 2.5.29.19 Criticality=false
	//	BasicConstraints:[
	//	  CA:true
	//	  PathLen:2147483647
	//	]


	public static final String TEST_TS1 =
			"/u3+7QAAAAIAAAABAAAAAgALcmliYm9uX3Jvb3QAAAFA9E6KkQAFWC41MDkAAAIyMIICLjCCAZeg" +
					"AwIBAgIBATANBgkqhkiG9w0BAQUFADBTMRgwFgYDVQQDDA9SaWJib25UZXN0Um9vdDExCzAJBgNV" +
					"BAsMAklUMRAwDgYDVQQKDAdOZXRmbGl4MQswCQYDVQQIDAJDQTELMAkGA1UEBhMCVVMwIBcNMTMw" +
					"OTA2MTcyNTIyWhgPMjExMzA4MTMxNzI1MjJaMFMxGDAWBgNVBAMMD1JpYmJvblRlc3RSb290MTEL" +
					"MAkGA1UECwwCSVQxEDAOBgNVBAoMB05ldGZsaXgxCzAJBgNVBAgMAkNBMQswCQYDVQQGEwJVUzCB" +
					"nzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAg8riOgT2Y39SQlZE+MWnOiKjREZzQ3ecvPf40oF8" +
					"9YPNGpBhJzIKdA0TR1vQ70p3Fl2+Y5txs1H2/iguOdFMBrSdv1H8qJG1UufaeYO++HBm3Mi2L02F" +
					"6fcTEEyXQMebKCWf04mxvLy5M6B5yMqZ9rHEZD+qsF4rXspx70bd0tUCAwEAAaMQMA4wDAYDVR0T" +
					"BAUwAwEB/zANBgkqhkiG9w0BAQUFAAOBgQBzTEn9AZniODYSRa+N7IvZu127rh+Sc6XWth68TBRj" +
					"hThDFARnGxxe2d3EFXB4xH7qcvLl3HQ3U6lIycyLabdm06D3/jzu68mkMToE5sHJmrYNHHTVl0aj" +
					"0gKFBQjLRJRlgJ3myUbbfrM+/a5g6S90TsVGTxXwFn5bDvdErsn8F8Hd41plMkW5ywsn6yFZMaFr" +
					"MxnX";


	//	Keystore type: JKS
	//	Keystore provider: SUN
	//
	//	Your keystore contains 1 entry
	//
	//	Alias name: ribbon_root
	//	Creation date: Sep 6, 2013
	//	Entry type: trustedCertEntry
	//
	//	Owner: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestRoot2
	//	Issuer: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestRoot2
	//	Serial number: 1
	//	Valid from: Fri Sep 06 10:26:22 PDT 2013 until: Sun Aug 13 10:26:22 PDT 2113
	//	Certificate fingerprints:
	//		 MD5:  44:64:E3:25:4F:D2:2C:8D:4D:B0:53:19:59:BD:B3:20
	//		 SHA1: 26:2F:41:6D:03:C7:D0:8E:4F:AF:0E:4F:29:E3:08:53:B7:3C:DB:EE
	//		 Signature algorithm name: SHA1withRSA
	//		 Version: 3
	//
	//	Extensions:
	//
	//	#1: ObjectId: 2.5.29.19 Criticality=false
	//	BasicConstraints:[
	//	  CA:true
	//	  PathLen:2147483647
	//	]

	public static final String TEST_TS2 =
			"/u3+7QAAAAIAAAABAAAAAgALcmliYm9uX3Jvb3QAAAFA9E92vgAFWC41MDkAAAIyMIICLjCCAZeg" +
					"AwIBAgIBATANBgkqhkiG9w0BAQUFADBTMRgwFgYDVQQDDA9SaWJib25UZXN0Um9vdDIxCzAJBgNV" +
					"BAsMAklUMRAwDgYDVQQKDAdOZXRmbGl4MQswCQYDVQQIDAJDQTELMAkGA1UEBhMCVVMwIBcNMTMw" +
					"OTA2MTcyNjIyWhgPMjExMzA4MTMxNzI2MjJaMFMxGDAWBgNVBAMMD1JpYmJvblRlc3RSb290MjEL" +
					"MAkGA1UECwwCSVQxEDAOBgNVBAoMB05ldGZsaXgxCzAJBgNVBAgMAkNBMQswCQYDVQQGEwJVUzCB" +
					"nzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAnEwAfuHYKRJviVB3RyV3+/mp4qjWZZd/q+fE2Z0k" +
					"o2N2rrC8fAw53KXwGOE5fED6wXd3B2zyoSFHVsWOeL+TUoohn+eHSfwH7xK+0oWC8IvUoXWehOft" +
					"grYtv9Jt5qNY5SmspBmyxFiaiAWQJYuf12Ycu4Gqg+P7mieMHgu6Do0CAwEAAaMQMA4wDAYDVR0T" +
					"BAUwAwEB/zANBgkqhkiG9w0BAQUFAAOBgQBNA0ask9eTYYhYA3bbmQZInxkBV74Gq/xorLlVygjn" +
					"OgyGYp4/L274qwlPMqnQRmVbezkug2YlUK8xbrjwCUvHq2XW38e2RjK5q3EXVkGJxgCBuHug/eIf" +
					"wD+/IEIE8aVkTW2j1QrrdkXDhRO5OsjvIVdy5/V4U0hVDnSo865ud9VQ/hZmOQuZItHViSoGSe2j" +
					"bbZk";

	//	Keystore type: JKS
	//	Keystore provider: SUN
	//
	//	Your keystore contains 1 entry
	//
	//	Alias name: ribbon_key
	//	Creation date: Sep 6, 2013
	//	Entry type: PrivateKeyEntry
	//	Certificate chain length: 1
	//	Certificate[1]:
	//	Owner: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestEndEntity1
	//	Issuer: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestRoot1
	//	Serial number: 64
	//	Valid from: Fri Sep 06 10:25:22 PDT 2013 until: Sun Aug 13 10:25:22 PDT 2113
	//	Certificate fingerprints:
	//		 MD5:  79:C5:F3:B2:B8:4C:F2:3F:2E:C7:67:FC:E7:04:BF:90
	//		 SHA1: B1:D0:4E:A6:D8:84:BF:8B:01:46:B6:EA:97:5B:A0:4E:13:8B:A9:DE
	//		 Signature algorithm name: SHA1withRSA
	//		 Version: 3

	public static final String TEST_KS1 =
			"/u3+7QAAAAIAAAABAAAAAQAKcmliYm9uX2tleQAAAUD0Toq4AAACuzCCArcwDgYKKwYBBAEqAhEB" +
					"AQUABIICo9fLN8zeXLcyx50+6B6gdXiUslZKY0SgLwL8kKNlQFiccD3Oc1yWjMnVC2QOdLsFzcVk" +
					"ROhgMH/nHfFeXFlvY5IYMXqhbEC37LjE52RtX5KHv4FLYxxZCHduAwO8UTPa603XzrJ0VTMJ6Hso" +
					"9+Ql76cGxPtIPcYm8IfqIY22as3NlKO4eMbiur9GLvuC57eql8vROaxGy8y657gc6kZMUyQOC+HG" +
					"a5M3DTFpjl4V6HHbXHhMNEk9eXHnrZwYVOJmOgdgIrNNHOyD4kE+k21C7rUHhLAwK84wKL/tW4k9" +
					"xnhOJK/L1RmycRIFWwXVi3u/3vi49bzdZsRLn73MdQkTe5p8oNZzG9sxg76u67ua6+99TMZYE1ay" +
					"5JCYgbr85KbRsoX9Hd5XBcSNzROKJl0To2tAF8eTTMRlhEy7JZyTF2M9877juNaregVwE3Tp+a/J" +
					"ACeNMyrxOQItNDam7a5dgBohpM8oJdEFqqj/S9BU7H5sR0XYo8TyIe1BV9zR5ZC/23fj5l5zkrri" +
					"TCMgMbvt95JUGOT0gSzxBMmhV+ZLxpmVz3M5P2pXX0DXGTKfuHSiBWrh1GAQL4BOVpuKtyXlH1/9" +
					"55/xY25W0fpLzMiQJV7jf6W69LU0FAFWFH9uuwf/sFph0S1QQXcQSfpYmWPMi1gx/IgIbvT1xSuI" +
					"6vajgFqv6ctiVbFAJ6zmcnGd6e33+Ao9pmjs5JPZP3rtAYd6+PxtlwUbGLZuqIVK4o68LEISDfvm" +
					"nGlk4/1+S5CILKVqTC6Ja8ojwUjjsNSJbZwHue3pOkmJQUNtuK6kDOYXgiMRLURbrYLyen0azWw8" +
					"C5/nPs5J4pN+irD/hhD6cupCnUJmzMw30u8+LOCN6GaM5fdCTQ2uQKF7quYuD+gR3lLNOqq7KAAA" +
					"AAEABVguNTA5AAACJTCCAiEwggGKoAMCAQICAWQwDQYJKoZIhvcNAQEFBQAwUzEYMBYGA1UEAwwP" +
					"UmliYm9uVGVzdFJvb3QxMQswCQYDVQQLDAJJVDEQMA4GA1UECgwHTmV0ZmxpeDELMAkGA1UECAwC" +
					"Q0ExCzAJBgNVBAYTAlVTMCAXDTEzMDkwNjE3MjUyMloYDzIxMTMwODEzMTcyNTIyWjBYMR0wGwYD" +
					"VQQDDBRSaWJib25UZXN0RW5kRW50aXR5MTELMAkGA1UECwwCSVQxEDAOBgNVBAoMB05ldGZsaXgx" +
					"CzAJBgNVBAgMAkNBMQswCQYDVQQGEwJVUzCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAydqk" +
					"AJuUcSF8dpUbNJWl+G1usgHtEFEbOMm54N/ZqGC7iSYs6EXfeoEyiHrMw/hdCKACERq2vuiuqan8" +
					"h6z65/DXIiHUyykGb/Z4NK1I0aCQLZG4Ek3sERilILWyy2NRpjUrvqDPr/mQgymXqpuYhSD81jHx" +
					"F84AOpTrnGsY7/sCAwEAATANBgkqhkiG9w0BAQUFAAOBgQAjvtRKNhb1R6XIuWaOxJ0XDLine464" +
					"Ie7LDfkE/KB43oE4MswjRh7nR9q6C73oa6TlIXmW6ysyKPp0vAyWHlq/zZhL3gNQ6faHuYHqas5s" +
					"nJQgvQpHAQh4VXRyZt1K8ZdsHg3Qbd4APTL0aRVQkxDt+Dxd6AsoRMKmO/c5CRwUFIV/CK7k5VSh" +
					"Sl5PRtH3PVj2vp84";

	//	Keystore type: JKS
	//	Keystore provider: SUN
	//
	//	Your keystore contains 1 entry
	//
	//	Alias name: ribbon_key
	//	Creation date: Sep 6, 2013
	//	Entry type: PrivateKeyEntry
	//	Certificate chain length: 1
	//	Certificate[1]:
	//	Owner: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestEndEntity2
	//	Issuer: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestRoot2
	//	Serial number: 64
	//	Valid from: Fri Sep 06 10:26:22 PDT 2013 until: Sun Aug 13 10:26:22 PDT 2113
	//	Certificate fingerprints:
	//		 MD5:  C3:AF:6A:DB:18:ED:70:22:83:73:9A:A5:DB:58:6D:04
	//		 SHA1: B7:D4:F0:87:A8:4E:49:0A:91:B1:7B:62:28:CA:A2:4A:0E:AE:40:CC
	//		 Signature algorithm name: SHA1withRSA
	//		 Version: 3
	//
	//
	//

	public static final String TEST_KS2 =
			"/u3+7QAAAAIAAAABAAAAAQAKcmliYm9uX2tleQAAAUD0T3bNAAACuzCCArcwDgYKKwYBBAEqAhEB" +
					"AQUABIICoysDP4inwmxxKZkS8EYMW3DCJCD1AmpwFHxJIzo2v9fMysg+vjKxsrvVyKG23ZcHcznI" +
					"ftmrEpriCCUZp+NNAf0EJWVAIzGenwrsd0+rI5I96gBOh9slJUzgucn7164R3XKNKk+VWcwGJRh+" +
					"IuHxVrwFN025pfhlBJXNGJg4ZlzB7ZwcQPYblBzhLbhS3vJ1Vc46pEYWpnjxmHaDSetaQIcueAp8" +
					"HnTUkFMXJ6t51N0u9QMPhBH7p7N8tNjaa5noSdxhSl/2Znj6r04NwQU1qX2n4rSWEnYaW1qOVkgx" +
					"YrQzxI2kSHZfQDM2T918UikboQvAS1aX4h5P3gVCDKLr3EOO6UYO0ZgLHUr/DZrhVKd1KAhnzaJ8" +
					"BABxot2ES7Zu5EzY9goiaYDA2/bkURmt0zDdKpeORb7r59XBZUm/8D80naaNnE45W/gBA9bCiDu3" +
					"R99xie447c7ZX9Jio25yil3ncv+npBO1ozc5QIgQnbEfxbbwii3//shvPT6oxYPrcwWBXnaJNC5w" +
					"2HDpCTXJZNucyjnNVVxC7p1ANNnvvZhgC0+GpEqmf/BW+fb9Qu+AXe0/h4Vnoe/Zs92vPDehpaKy" +
					"oe+jBlUNiW2bpR88DSqxVcIu1DemlgzPa1Unzod0FdrOr/272bJnB2zAo4OBaBSv3KNf/rsMKjsU" +
					"X2Po77+S+PKoQkqd8KJFpmLEb0vxig9JsrTDJXLf4ebeSA1W7+mBotimMrp646PA3NciMSbS4csh" +
					"A7o/dBYhHlEosVgThm1JknIKhemf+FZsOzR3bJDT1oXJ/GhYpfzlCLyVFBeVP0KRnhih4xO0MEO7" +
					"Q21DBaTTqAvUo7Iv3/F3mGMOanLcLgoRoq3moQ7FhfCDRtRAPA1qT2+pxPG5wqlGeYc6McOvogAA" +
					"AAEABVguNTA5AAACJTCCAiEwggGKoAMCAQICAWQwDQYJKoZIhvcNAQEFBQAwUzEYMBYGA1UEAwwP" +
					"UmliYm9uVGVzdFJvb3QyMQswCQYDVQQLDAJJVDEQMA4GA1UECgwHTmV0ZmxpeDELMAkGA1UECAwC" +
					"Q0ExCzAJBgNVBAYTAlVTMCAXDTEzMDkwNjE3MjYyMloYDzIxMTMwODEzMTcyNjIyWjBYMR0wGwYD" +
					"VQQDDBRSaWJib25UZXN0RW5kRW50aXR5MjELMAkGA1UECwwCSVQxEDAOBgNVBAoMB05ldGZsaXgx" +
					"CzAJBgNVBAgMAkNBMQswCQYDVQQGEwJVUzCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAtOvK" +
					"fBMC/oMo4xf4eYGN7hTNn+IywaSaVYndqECIfuznoIqRKSbeCKPSGs4CN1D+u3E2UoXcsDmTguHU" +
					"fokDA7sLUu8wD5ndAXfCkP3gXlFtUpNz/jPaXDsMFntTn2BdLKccxRxNwtwC0zzwIdtx9pw/Ru0g" +
					"NXIQnPi50aql5WcCAwEAATANBgkqhkiG9w0BAQUFAAOBgQCYWM2ZdBjG3jCvMw/RkebMLkEDRxVM" +
					"XU63Ygo+iCZUk8V8d0/S48j8Nk/hhVGHljsHqE/dByEF77X6uHaDGtt3Xwe+AYPofoJukh89jKnT" +
					"jEDtLF+y5AVfz6b2z3TnJcuigMr4ZtBFv18R00KLnVAznl/waXG8ix44IL5ss6nRZBJE4jr+ZMG9" +
					"9I4P1YhySxo3Qd3g";



	private static String SERVICE_URI1;
	private static int PORT1;

	private static String SERVICE_URI2;
	private static int PORT2;

	private static SimpleSSLTestServer testServer1;
	private static SimpleSSLTestServer testServer2;

	private static File FILE_TS1;
	private static File FILE_KS1;

	private static File FILE_TS2;
	private static File FILE_KS2;

	private static String PASSWORD = "changeit";


	@BeforeClass
	public static void init() throws Exception {

		// setup server 1, will use first keystore/truststore with client auth
		PORT1 = new Random().nextInt(1000) + 4000;
		SERVICE_URI1 = "https://127.0.0.1:" + PORT1 + "/";

		// jks format
		byte[] sampleTruststore1 = Base64.decode(TEST_TS1);
		byte[] sampleKeystore1 = Base64.decode(TEST_KS1);

		FILE_KS1 = File.createTempFile("SecureGetTest1", ".keystore");
		FILE_TS1 = File.createTempFile("SecureGetTest1", ".truststore");

		FileOutputStream keystoreFileOut = new FileOutputStream(FILE_KS1);
		keystoreFileOut.write(sampleKeystore1);
		keystoreFileOut.close();

		FileOutputStream truststoreFileOut = new FileOutputStream(FILE_TS1);
		truststoreFileOut.write(sampleTruststore1);
		truststoreFileOut.close();

		try{
			testServer1 = new SimpleSSLTestServer(FILE_TS1, PASSWORD, FILE_KS1, PASSWORD, PORT1, true);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}

		// setup server 2, will use second keystore truststore without client auth

		PORT2 = PORT1 + 1;
		SERVICE_URI2 = "https://127.0.0.1:" + PORT2 + "/";

		// jks format
		byte[] sampleTruststore2 = Base64.decode(TEST_TS2);
		byte[] sampleKeystore2 = Base64.decode(TEST_KS2);

		FILE_KS2 = File.createTempFile("SecureGetTest2", ".keystore");
		FILE_TS2 = File.createTempFile("SecureGetTest2", ".truststore");

		keystoreFileOut = new FileOutputStream(FILE_KS2);
		keystoreFileOut.write(sampleKeystore2);
		keystoreFileOut.close();

		truststoreFileOut = new FileOutputStream(FILE_TS2);
		truststoreFileOut.write(sampleTruststore2);
		truststoreFileOut.close();

		try{
			testServer2 = new SimpleSSLTestServer(FILE_TS2, PASSWORD, FILE_KS2, PASSWORD, PORT2, false);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@AfterClass
	public static void shutDown(){

		try{
			testServer1.close();
		}catch(Exception e){
			e.printStackTrace();
		}

		try{
			testServer2.close();
		}catch(Exception e){
			e.printStackTrace();
		}

	}

	@Test
	public void testSunnyDay() throws Exception {

		AbstractConfiguration cm = ConfigurationManager.getConfigInstance();

		String name = "GetPostSecureTest" + ".testSunnyDay";

		String configPrefix = name + "." + "ribbon";

		cm.setProperty(configPrefix + "." + CommonClientConfigKey.IsSecure, "true");
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.SecurePort, Integer.toString(PORT1));
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.IsHostnameValidationRequired, "false");
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.IsClientAuthRequired, "true");
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.KeyStore, FILE_KS1.getAbsolutePath());
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.KeyStorePassword, PASSWORD);
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.TrustStore, FILE_TS1.getAbsolutePath());
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.TrustStorePassword, PASSWORD);

		RestClient rc = (RestClient) ClientFactory.getNamedClient(name);

		testServer1.accept();

		URI getUri = new URI(SERVICE_URI1 + "test/");
		MultivaluedMapImpl params = new MultivaluedMapImpl();
		params.add("name", "test");
		HttpClientRequest request = HttpClientRequest.newBuilder().setUri(getUri).setQueryParams(params).build();
		HttpClientResponse response = rc.execute(request);
		assertEquals(200, response.getStatus());
	}


	@Test
	public void testSunnyDayNoClientAuth() throws Exception{

		AbstractConfiguration cm = ConfigurationManager.getConfigInstance();

		String name = "GetPostSecureTest" + ".testSunnyDayNoClientAuth";

		String configPrefix = name + "." + "ribbon";

		cm.setProperty(configPrefix + "." + CommonClientConfigKey.IsSecure, "true");
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.SecurePort, Integer.toString(PORT2));
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.IsHostnameValidationRequired, "false");
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.TrustStore, FILE_TS2.getAbsolutePath());
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.TrustStorePassword, PASSWORD);

		RestClient rc = (RestClient) ClientFactory.getNamedClient(name);

		testServer2.accept();

		URI getUri = new URI(SERVICE_URI2 + "test/");
		MultivaluedMapImpl params = new MultivaluedMapImpl();
		params.add("name", "test");
		HttpClientRequest request = HttpClientRequest.newBuilder().setUri(getUri).setQueryParams(params).build();
		HttpClientResponse response = rc.execute(request);
		assertEquals(200, response.getStatus());
	}


	@Test
	public void testFailsWithHostNameValidationOn() throws Exception {

		AbstractConfiguration cm = ConfigurationManager.getConfigInstance();

		String name = "GetPostSecureTest" + ".testFailsWithHostNameValidationOn";

		String configPrefix = name + "." + "ribbon";

		cm.setProperty(configPrefix + "." + CommonClientConfigKey.IsSecure, "true");
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.SecurePort, Integer.toString(PORT1));
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.IsHostnameValidationRequired, "true"); // <--
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.IsClientAuthRequired, "true");
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.KeyStore, FILE_KS1.getAbsolutePath());
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.KeyStorePassword, PASSWORD);
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.TrustStore, FILE_TS1.getAbsolutePath());
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.TrustStorePassword, PASSWORD);

		RestClient rc = (RestClient) ClientFactory.getNamedClient(name);

		testServer1.accept();

		URI getUri = new URI(SERVICE_URI1 + "test/");
		MultivaluedMapImpl params = new MultivaluedMapImpl();
		params.add("name", "test");
		HttpClientRequest request = HttpClientRequest.newBuilder().setUri(getUri).setQueryParams(params).build();

		try{
			rc.execute(request);

			fail("expecting ssl hostname validation error");
		}catch(ClientHandlerException che){
			assertTrue(che.getMessage().indexOf("hostname in certificate didn't match") > -1);
		}
	}

	@Test
	public void testClientRejectsWrongServer() throws Exception{

		AbstractConfiguration cm = ConfigurationManager.getConfigInstance();

		String name = "GetPostSecureTest" + ".testClientRejectsWrongServer";

		String configPrefix = name + "." + "ribbon";

		cm.setProperty(configPrefix + "." + CommonClientConfigKey.IsSecure, "true");
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.SecurePort, Integer.toString(PORT2));
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.IsHostnameValidationRequired, "false");
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.TrustStore, FILE_TS1.getAbsolutePath()); // <--
		cm.setProperty(configPrefix + "." + CommonClientConfigKey.TrustStorePassword, PASSWORD);

		RestClient rc = (RestClient) ClientFactory.getNamedClient(name);

		testServer2.accept();

		URI getUri = new URI(SERVICE_URI2 + "test/");
		MultivaluedMapImpl params = new MultivaluedMapImpl();
		params.add("name", "test");
		HttpClientRequest request = HttpClientRequest.newBuilder().setUri(getUri).setQueryParams(params).build();
		try{
			rc.execute(request);

			fail("expecting ssl hostname validation error");

		}catch(ClientHandlerException che){
			assertTrue(che.getMessage().indexOf("peer not authenticated") > -1);
		}
	}



}
