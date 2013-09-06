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

package com.netflix.client.testutil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Ignore;

import com.google.common.io.Closeables;

/**
 *
 * A simple SSL(TLS) server for which we can test against
 * to ensure that the SSL connection can (or cannot) be established.
 *
 * @author jzarfoss
 *
 */
@Ignore
public class SimpleSSLTestServer {

	private static final String NL = System.getProperty("line.separator");

	private static final String CANNED_RESPONSE =
									"HTTP/1.0 200 OK" + NL +
									"Content-Type: text/plain" + NL +
									"Content-Length: 5" + NL + NL +
									"hello" + NL;

	private final ServerSocket ss;

	@edu.umd.cs.findbugs.annotations.SuppressWarnings
	public SimpleSSLTestServer(final File truststore, final String truststorePass,
			final File keystore, final String keystorePass, final int port, final boolean clientAuthRequred) throws Exception{

			KeyStore ks = KeyStore.getInstance("JKS");
			ks.load(new FileInputStream(keystore), keystorePass.toCharArray());
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, keystorePass.toCharArray());

			KeyStore ts = KeyStore.getInstance("JKS");
			ts.load(new FileInputStream(truststore), keystorePass.toCharArray());
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ts);

			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

			ss = sc.getServerSocketFactory().createServerSocket(port);

			((SSLServerSocket) ss).setNeedClientAuth(clientAuthRequred);
	}

	public void accept() throws Exception{

		new Thread(){

			@Override
			public void run(){

				Socket sock = null;
				BufferedReader reader = null;
				BufferedWriter writer = null;

				try{
					sock = ss.accept();

					reader = new BufferedReader(new InputStreamReader(sock.getInputStream(), Charset.defaultCharset()));
					writer = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), Charset.defaultCharset()));

					reader.readLine(); // we really don't care what the client says, he's getting the special regardless...

					writer.write(CANNED_RESPONSE);
					writer.flush();

				}catch(Exception e){
					e.printStackTrace();
				}finally{
					try{
						Closeables.close(reader, true);
						Closeables.close(writer, true);
						sock.close();
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			}
		}.start();
	}

	public void close() throws Exception{
		ss.close();
	}


}
