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
package com.netflix.http4;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

public class NFHttpClientTest {
	
    @Test
    public void testDefaultClient() throws Exception {
    	NFHttpClient client = NFHttpClientFactory.getDefaultClient();
    	HttpGet get = new HttpGet("http://www.google.com"); // uri
    	// this is not the overridable method
    	HttpResponse response = client.execute(get);
    	HttpEntity entity = response.getEntity();
    	String contentStr = EntityUtils.toString(entity);
    	assertTrue(contentStr.length() > 0);
    }
    
    @Test
    public void testNFHttpClient() throws Exception {
        NFHttpClient client = NFHttpClientFactory.getNFHttpClient("www.google.com", 80);
        ThreadSafeClientConnManager cm = (ThreadSafeClientConnManager) client.getConnectionManager();
    	cm.setDefaultMaxPerRoute(10);
        HttpGet get = new HttpGet("www.google.com");
        ResponseHandler<Integer> respHandler = new ResponseHandler<Integer>(){
        		public Integer handleResponse(HttpResponse response)
                 throws ClientProtocolException, IOException {
        			HttpEntity entity = response.getEntity();
        			String contentStr = EntityUtils.toString(entity);
            		return contentStr.length();
        		}
        };
        long contentLen = client.execute(get, respHandler);
        assertTrue(contentLen > 0);
    }


    @Test
    public void testMultiThreadedClient() throws Exception {

        NFHttpClient client = (NFHttpClient) NFHttpClientFactory
                .getNFHttpClient("hc.apache.org", 80);
       
        ThreadSafeClientConnManager cm = (ThreadSafeClientConnManager) client.getConnectionManager();
        cm.setDefaultMaxPerRoute(10);
        
        HttpHost target = new HttpHost("hc.apache.org", 80);

        // create an array of URIs to perform GETs on
        String[] urisToGet = { "/", "/httpclient-3.x/status.html",
                "/httpclient-3.x/methods/",
                "http://svn.apache.org/viewvc/httpcomponents/oac.hc3x/" };

        // create a thread for each URI
        GetThread[] threads = new GetThread[urisToGet.length];
        for (int i = 0; i < threads.length; i++) {
            HttpGet get = new HttpGet(urisToGet[i]);
            threads[i] = new GetThread(client, target, get, i + 1);
        }

        // start the threads
        for (int j = 0; j < threads.length; j++) {
            threads[j].start();
        }

    }
    
    /**
     * A thread that performs a GET.
     */
    static class GetThread extends Thread {
        
        private HttpClient httpClient;
        private HttpHost target;
        private HttpGet request;
        private int id;
        
        public GetThread(HttpClient httpClient, HttpHost target, HttpGet request, int id) {
            this.httpClient = httpClient;
            this.target = target;
            this.request = request;
            this.id = id;
        }
        
        /**
         * Executes the GetMethod and prints some satus information.
         */
        public void run() {
            
            try {
                
                System.out.println(id + " - about to get something from " + request.getURI());
                // execute the method
                HttpResponse resp = httpClient.execute(target, request);
                
                System.out.println(id + " - get executed");
                // get the response body as an array of bytes
                byte[] bytes = EntityUtils.toByteArray(resp.getEntity());
                
                System.out.println(id + " - " + bytes.length + " bytes read");
                
            } catch (Exception e) {
                System.out.println(id + " - error: " + e);
            } finally {
                System.out.println(id + " - connection released");
            }
        }
       
    }

}
