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

import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 *
 * SSL Socket factory that will accept all remote endpoints.
 *
 * This should be used only for testing connections/connectivity.
 *
 * Following similar pattern as load-balancers here, which is to take an IClientConfig
 *
 * @author jzarfoss
 *
 */
public class AcceptAllSocketFactory extends SSLSocketFactory implements IClientConfigAware {

    public AcceptAllSocketFactory() throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        super(new TrustStrategy() {

            @Override
            public boolean isTrusted(final X509Certificate[] chain, String authType) throws CertificateException {
                return true;
            }

        }, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    /**
     * In the case of this factory the intent is to ensure that a truststore is not set,
     * as this does not make sense in the context of an accept-all policy
     */
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        if (clientConfig == null) {
            return;
        }

        if (clientConfig.getOrDefault(CommonClientConfigKey.TrustStore) != null) {
            throw new IllegalArgumentException("Client configured with an AcceptAllSocketFactory cannot utilize a truststore");
        }
    }
}
