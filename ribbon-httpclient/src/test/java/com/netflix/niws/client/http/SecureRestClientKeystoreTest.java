package com.netflix.niws.client.http;

import java.io.File;

import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.Test;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.config.ConfigurationManager;
import com.sun.jersey.core.util.Base64;

public class SecureRestClientKeystoreTest {

	// dummy truststore from a common verisign root.
	// take heed that it expires in 2036


	//	Alias name: sample_root
	//	Creation date: Aug 30, 2013
	//	Entry type: trustedCertEntry
	//
	//	Owner: CN=VeriSign Class 3 Public Primary Certification Authority - G5, OU="(c) 2006 VeriSign, Inc. - For authorized use only", OU=VeriSign Trust Network, O="VeriSign, Inc.", C=US
	//	Issuer: CN=VeriSign Class 3 Public Primary Certification Authority - G5, OU="(c) 2006 VeriSign, Inc. - For authorized use only", OU=VeriSign Trust Network, O="VeriSign, Inc.", C=US
	//	Serial number: 18dad19e267de8bb4a2158cdcc6b3b4a
	//	Valid from: Tue Nov 07 16:00:00 PST 2006 until: Wed Jul 16 16:59:59 PDT 2036
	//	Certificate fingerprints:
	//		 MD5:  CB:17:E4:31:67:3E:E2:09:FE:45:57:93:F3:0A:FA:1C
	//		 SHA1: 4E:B6:D5:78:49:9B:1C:CF:5F:58:1E:AD:56:BE:3D:9B:67:44:A5:E5
	//		 Signature algorithm name: SHA1withRSA
	//		 Version: 3
	//
	//	Extensions: 
	//
	//	#1: ObjectId: 2.5.29.15 Criticality=true
	//	KeyUsage [
	//	  Key_CertSign
	//	  Crl_Sign
	//	]
	//
	//	#2: ObjectId: 2.5.29.19 Criticality=true
	//	BasicConstraints:[
	//	  CA:true
	//	  PathLen:2147483647
	//	]
	//
	//	#3: ObjectId: 1.3.6.1.5.5.7.1.12 Criticality=false
	//
	//	#4: ObjectId: 2.5.29.14 Criticality=false
	//	SubjectKeyIdentifier [
	//	KeyIdentifier [
	//	0000: 7F D3 65 A7 C2 DD EC BB   F0 30 09 F3 43 39 FA 02  ..e......0..C9..
	//	0010: AF 33 31 33                                        .313
	//	]
	//	]

	private static final String DUMMY_TRUSTSTORE_PASSWORD = "changeit";
	
	private static final String DUMMY_TRUSTSTORE = 
			"/u3+7QAAAAIAAAABAAAAAgALc2FtcGxlX3Jvb3QAAAFA0cbCLgAFWC41MDkAAATXMIIE0zCCA7ug" + 
					"AwIBAgIQGNrRniZ96LtKIVjNzGs7SjANBgkqhkiG9w0BAQUFADCByjELMAkGA1UEBhMCVVMxFzAV" + 
					"BgNVBAoTDlZlcmlTaWduLCBJbmMuMR8wHQYDVQQLExZWZXJpU2lnbiBUcnVzdCBOZXR3b3JrMTow" + 
					"OAYDVQQLEzEoYykgMjAwNiBWZXJpU2lnbiwgSW5jLiAtIEZvciBhdXRob3JpemVkIHVzZSBvbmx5" + 
					"MUUwQwYDVQQDEzxWZXJpU2lnbiBDbGFzcyAzIFB1YmxpYyBQcmltYXJ5IENlcnRpZmljYXRpb24g" + 
					"QXV0aG9yaXR5IC0gRzUwHhcNMDYxMTA4MDAwMDAwWhcNMzYwNzE2MjM1OTU5WjCByjELMAkGA1UE" + 
					"BhMCVVMxFzAVBgNVBAoTDlZlcmlTaWduLCBJbmMuMR8wHQYDVQQLExZWZXJpU2lnbiBUcnVzdCBO" + 
					"ZXR3b3JrMTowOAYDVQQLEzEoYykgMjAwNiBWZXJpU2lnbiwgSW5jLiAtIEZvciBhdXRob3JpemVk" + 
					"IHVzZSBvbmx5MUUwQwYDVQQDEzxWZXJpU2lnbiBDbGFzcyAzIFB1YmxpYyBQcmltYXJ5IENlcnRp" + 
					"ZmljYXRpb24gQXV0aG9yaXR5IC0gRzUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCv" + 
					"JAgIKXo1nmAMqudLO07cfLw8RRy7K+D+KQL5VwijZIUVJ/XxrcgxiV0i6CqqpkKzj/i5Vbext0uz" + 
					"/o9+B1fs70PbZmIVYc9gDaTY3vjgw2IIPVQT60nKWVSFJuUrjxuf6/WhkcIzSdhDY2pSS9KP6HBR" + 
					"TdGJaXvHcPaz3BJ023tdS1bTlr8Vd6Gw9KIl8q8ckmcY5fQGBO+QueQA5N06tRn/Arr0PO7gi+s3" + 
					"i+z016zy9vA9r911kTMZHRxAy3QkGSGT2RT+rCpSx4/VBEnkjWNHiDxpg8v+R70rfk/Fla4OndTR" + 
					"Q8Bnc+MUCH7lP59zuDMKz10/NIeWiu5T6CUVAgMBAAGjgbIwga8wDwYDVR0TAQH/BAUwAwEB/zAO" + 
					"BgNVHQ8BAf8EBAMCAQYwbQYIKwYBBQUHAQwEYTBfoV2gWzBZMFcwVRYJaW1hZ2UvZ2lmMCEwHzAH" + 
					"BgUrDgMCGgQUj+XTGoasjY5rw8+AatRIGCx7GS4wJRYjaHR0cDovL2xvZ28udmVyaXNpZ24uY29t" + 
					"L3ZzbG9nby5naWYwHQYDVR0OBBYEFH/TZafC3ey78DAJ80M5+gKvMzEzMA0GCSqGSIb3DQEBBQUA" + 
					"A4IBAQCTJEowX2LP2BqYLz3q3JktvXf2pXkiOOzEp6B4Eq1iDkVwZMXnl2YtmAl+X6/WzChl8gGq" + 
					"CBpH3vn5fJJaCGkgDdk+bW48DW7Y5gaRQBi5+MHt39tBquCWIMnNZBU4gcmU7qKEKQsTb47bDN0l" + 
					"AtukixlE0kF6BWlKWE9gyn6CagsCqiUXObXbf+eEZSqVir2G3l6BFoMtEMze/aiCKm0oHw0LxOXn" + 
					"GiYZ4fQRbxC1lfznQgUy286dUV4otp6F01vvpX1FQHKOtw5rDgb7MzVIcbidJ4vEZV8NhnacRHr2" + 
					"lVz2XTIIM6RUthg/aFzyQkqFOFSDX9HoLPKsEdao7WNq8RZLLllY/uSyRqtKBTGTmf/ktGM=";

	@Test
	public void testGetKeystoreWithClientAuth() throws Exception{

		// jks format
		byte[] dummyTruststore = Base64.decode(DUMMY_TRUSTSTORE);
		
		File tempKeystore = File.createTempFile(this.getClass().getName(), ".keystore");
		File tempTruststore = File.createTempFile(this.getClass().getName(), ".truststore");

		AbstractConfiguration cm = ConfigurationManager.getConfigInstance();
		
		String configPrefix = this.getClass().getName() + ".test1.";
		
		cm.setProperty(configPrefix + CommonClientConfigKey.IsSecure, "true");
		cm.setProperty(configPrefix + CommonClientConfigKey.IsClientAuthRequired, "true");
		cm.setProperty(configPrefix + CommonClientConfigKey.KeyStore, tempKeystore.getAbsolutePath());
		cm.setProperty(configPrefix + CommonClientConfigKey.KeyStorePassword, DUMMY_TRUSTSTORE_PASSWORD);
		cm.setProperty(configPrefix + CommonClientConfigKey.TrustStore, tempTruststore.getAbsolutePath());
		cm.setProperty(configPrefix + CommonClientConfigKey.TrustStorePassword, DUMMY_TRUSTSTORE_PASSWORD);
		
		RestClient client = (RestClient) ClientFactory.getNamedClient(configPrefix);
		
		
		

	}

}
