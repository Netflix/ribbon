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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;

/**
 * Netflix extension of Apache 4.0 HttpClient
 * Just so we can wrap around some features.
 * 
 * @author stonse
 *
 */
public class NFHttpClient extends DefaultHttpClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(NFHttpClient.class);

	protected static final String EXECUTE_TRACER = "NFHttpClient__EXECUTE_REQ";

	private HttpHost httpHost = null;
	private HttpRoute httpRoute = null;

	private static AtomicInteger numNonNamedHttpClients = new AtomicInteger();

	private final String name;

	ConnectionPoolCleaner connPoolCleaner;

	DynamicIntProperty connIdleEvictTimeMilliSeconds;

	private DynamicIntProperty retriesProperty;
	private DynamicIntProperty sleepTimeFactorMsProperty;
	
	private Timer tracer; 

	protected NFHttpClient(String host, int port){
		super(new ThreadSafeClientConnManager());
		this.name = "UNNAMED_" + numNonNamedHttpClients.incrementAndGet();
		httpHost = new HttpHost(host, port);
		httpRoute = new HttpRoute(httpHost);
		init();
	}   

	protected NFHttpClient(){
		super(new ThreadSafeClientConnManager());
		this.name = "UNNAMED_" + numNonNamedHttpClients.incrementAndGet();
		init();
	}

	protected NFHttpClient(String name){
		super(new ThreadSafeClientConnManager());
		this.name = name;
		init();
	}

	void init() {
		HttpParams params = getParams();

		HttpProtocolParams.setContentCharset(params, "UTF-8");  
		params.setParameter(ClientPNames.CONNECTION_MANAGER_FACTORY_CLASS_NAME, 
				ThreadSafeClientConnManager.class.getName());

		// set up default headers
		List<Header> defaultHeaders = new ArrayList<Header>();
		defaultHeaders.add(new BasicHeader("Netflix.NFHttpClient.Version", "1.0"));
		defaultHeaders.add(new BasicHeader("X-netflix-httpclientname", name));
		params.setParameter(ClientPNames.DEFAULT_HEADERS, defaultHeaders);

		connPoolCleaner = new ConnectionPoolCleaner(name, this.getConnectionManager());

		this.retriesProperty = DynamicPropertyFactory.getInstance().getIntProperty(this.name + ".nfhttpclient" + ".retries", 3);
		this.sleepTimeFactorMsProperty = DynamicPropertyFactory.getInstance().getIntProperty(this.name + ".nfhttpclient"+ ".sleepTimeFactorMs", 10);
		setHttpRequestRetryHandler(
				new NFHttpMethodRetryHandler(this.name, this.retriesProperty.get(), false,
						this.sleepTimeFactorMsProperty.get()));
	    tracer = Monitors.newTimer(EXECUTE_TRACER, TimeUnit.MILLISECONDS);
        Monitors.registerObject(name, this);
	}

	public void initConnectionCleanerTask(){

		//set the Properties
		connPoolCleaner.setConnIdleEvictTimeMilliSeconds(getConnIdleEvictTimeMilliSeconds());// set FastProperty reference
		// for this named httpclient - so we can override it later if we want to
		//init the Timer Task
		//note that we can change the idletime settings after the start of the Thread
		connPoolCleaner.initTask();

	}

	@Monitor(name = "connPoolCleaner", type = DataSourceType.INFORMATIONAL)
	public ConnectionPoolCleaner getConnPoolCleaner() {
		return connPoolCleaner;
	}

	@Monitor(name = "connIdleEvictTimeMilliSeconds", type = DataSourceType.INFORMATIONAL)
	public DynamicIntProperty getConnIdleEvictTimeMilliSeconds() {
		if (connIdleEvictTimeMilliSeconds == null){
			connIdleEvictTimeMilliSeconds = DynamicPropertyFactory.getInstance().getIntProperty(name + ".nfhttpclient.connIdleEvictTimeMilliSeconds", 
					NFHttpClientConstants.DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS);
		}
		return connIdleEvictTimeMilliSeconds;
	}

	@Monitor(name="connectionsInPool", type = DataSourceType.GAUGE)    
	public int getConnectionsInPool() {
		ClientConnectionManager connectionManager = this.getConnectionManager();
		if (connectionManager != null) {
			return ((ThreadSafeClientConnManager)connectionManager).getConnectionsInPool();
		} else {
			return 0;
		}
	}

	@Monitor(name = "maxTotalConnections", type = DataSourceType.INFORMATIONAL)
	public int getMaxTotalConnnections() {
		ClientConnectionManager connectionManager = this.getConnectionManager();
		if (connectionManager != null) {
			return ((ThreadSafeClientConnManager)connectionManager).getMaxTotal();
		} else {
			return 0;
		}
	}

	@Monitor(name = "maxConnectionsPerHost", type = DataSourceType.INFORMATIONAL)
	public int getMaxConnectionsPerHost() {
		ClientConnectionManager connectionManager = this.getConnectionManager();
		if (connectionManager != null) {
			if(httpRoute == null)
				return ((ThreadSafeClientConnManager)connectionManager).getDefaultMaxPerRoute();
			else
				return ((ThreadSafeClientConnManager)connectionManager).getMaxForRoute(httpRoute);
		} else {
			return 0;
		}
	}

	@Monitor(name = "numRetries", type = DataSourceType.INFORMATIONAL)
	public int getNumRetries() {
		return this.retriesProperty.get();
	}

	public void setConnIdleEvictTimeMilliSeconds(DynamicIntProperty connIdleEvictTimeMilliSeconds) {
		this.connIdleEvictTimeMilliSeconds = connIdleEvictTimeMilliSeconds;
	}

	@Monitor(name = "sleepTimeFactorMs", type = DataSourceType.INFORMATIONAL)
	public int getSleepTimeFactorMs() {
		return this.sleepTimeFactorMsProperty.get();
	}

	// copied from httpclient source code
	private static HttpHost determineTarget(HttpUriRequest request) throws ClientProtocolException {
		// A null target may be acceptable if there is a default target.
		// Otherwise, the null target is detected in the director.
		HttpHost target = null;
		URI requestURI = request.getURI();
		if (requestURI.isAbsolute()) {
			target = URIUtils.extractHost(requestURI);
			if (target == null) {
				throw new ClientProtocolException(
						"URI does not specify a valid host name: " + requestURI);
			}
		}
		return target;
	}
	
	@Override
	public <T> T execute(
			final HttpUriRequest request,
			final ResponseHandler<? extends T> responseHandler)
					throws IOException, ClientProtocolException {
		return this.execute(request, responseHandler, null);
	}

	@Override
	public <T> T execute(
			final HttpUriRequest request,
			final ResponseHandler<? extends T> responseHandler,
			final HttpContext context)
					throws IOException, ClientProtocolException {
		HttpHost target = null;
		if(httpHost == null)
			target = determineTarget(request);
		else
			target = httpHost;
		return this.execute(target, request, responseHandler, context);
	}

	@Override
	public <T> T execute(
			final HttpHost target,
			final HttpRequest request,
			final ResponseHandler<? extends T> responseHandler)
					throws IOException, ClientProtocolException {
		return this.execute(target, request, responseHandler, null);
	}

	@Override
	public <T> T execute(
			final HttpHost target,
			final HttpRequest request,
			final ResponseHandler<? extends T> responseHandler,
			final HttpContext context)
					throws IOException, ClientProtocolException {
	    Stopwatch sw = tracer.start();
		try{
			// TODO: replaced method.getQueryString() with request.getRequestLine().getUri()
			LOGGER.debug("Executing HTTP method: {}, uri: {}", request.getRequestLine().getMethod(), request.getRequestLine().getUri());
			return super.execute(target, request, responseHandler, context);
		}finally{
			sw.stop();
		}
	}
}