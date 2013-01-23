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

/**
 * A simple netflix specific extension class for {@link org.apache.commons.httpclient.DefaultHttpMethodRetryHandler}.
 * 
 * Provides a configurable override for the number of retries. Also waits for a configurable time before retry.
 */
import java.io.IOException;

import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.servo.monitor.DynamicCounter;

public class NFHttpMethodRetryHandler extends DefaultHttpRequestRetryHandler {
	private static final String RETRY_COUNTER = "PLATFORM:NFttpClient:Retries:";
	private Logger logger = LoggerFactory.getLogger(NFHttpMethodRetryHandler.class);
	private int sleepTimeFactorMs;
	private String httpClientName;

	/**
	 * Creates a new NFHttpMethodRetryHandler.
	 * @param httpClientName - the name of the nfhttpclient
	 * @param retryCount the number of times a method will be retried
	 * @param requestSentRetryEnabled if true, methods that have successfully sent their request will be retried
	 * @param sleepTimeFactorMs number of milliseconds to sleep before the next try. This factor is used along with execution count
	 * to determine the sleep time (ie) executionCount * sleepTimeFactorMs
	 */
	public NFHttpMethodRetryHandler(String httpClientName, int retryCount, boolean requestSentRetryEnabled, int sleepTimeFactorMs) {
		super(retryCount, requestSentRetryEnabled);
		this.httpClientName = httpClientName;
		this.sleepTimeFactorMs = sleepTimeFactorMs;
	}

	@Override
	@edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "ICAST_INTEGER_MULTIPLY_CAST_TO_LONG")
	public boolean retryRequest(
			final IOException exception, 
			int executionCount, 
			HttpContext context
			) {
		if (super.retryRequest(exception, executionCount, context)) {
			HttpRequest request = (HttpRequest)
					context.getAttribute(ExecutionContext.HTTP_REQUEST);
			String methodName = request.getRequestLine().getMethod();
			String path = "UNKNOWN_PATH";
			if(request instanceof HttpUriRequest) {
				HttpUriRequest uriReq = (HttpUriRequest) request;
				path = uriReq.getURI().toString();
			}
			try {
				Thread.sleep(executionCount * this.sleepTimeFactorMs);
			}
			catch (InterruptedException e) {
				logger.warn("Interrupted while sleep before retrying http method " + methodName + " " + path, e);
			}
			DynamicCounter.increment(RETRY_COUNTER + methodName + ":" + path);
			return true;
		}
		return false;
	}
}
