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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.IPrimeConnection;
import com.netflix.client.config.IClientConfig;
import com.netflix.http4.NFHttpClient;
import com.netflix.http4.NFHttpClientFactory;
import com.netflix.loadbalancer.Server;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.params.HttpConnectionParams;

/**
 * An implementation of {@link IPrimeConnection} using Apache HttpClient.
 * 
 * @author awang
 *
 */
public class HttpPrimeConnection implements IPrimeConnection {

    private static final Logger logger = LoggerFactory.getLogger(HttpPrimeConnection.class);
    
    private NFHttpClient client;
    
    public HttpPrimeConnection() {
    }
    
    @Override
    public boolean connect(Server server, String primeConnectionsURIPath) throws Exception {
        String url = "http://" + server.getHostPort() + primeConnectionsURIPath;
        logger.debug("Trying URL: {}", url);
        HttpUriRequest get = new HttpGet(url);
        HttpResponse response = null;
        try {
            response = client.execute(get);
            if (logger.isDebugEnabled() && response.getStatusLine() != null) {
                logger.debug("Response code:" + response.getStatusLine().getStatusCode());
            }
        } finally {
           get.abort();
        }
        return true;
    }

    @Override
    public void initWithNiwsConfig(IClientConfig niwsClientConfig) {
        client = NFHttpClientFactory.getNamedNFHttpClient(niwsClientConfig.getClientName() + "-PrimeConnsClient", false); 
        HttpConnectionParams.setConnectionTimeout(client.getParams(), 2000);        
    }
}
