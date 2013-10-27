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
package com.netflix.client;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;

/**
 * Abstract class that provides the integration of client with load balancers.
 * 
 * @author awang
 *
 */
public abstract class AbstractLoadBalancerAwareClient<S extends ClientRequest, T extends IResponse> extends LoadBalancerContext<S, T> implements IClient<S, T> {    
    
    private static final Logger logger = LoggerFactory.getLogger(AbstractLoadBalancerAwareClient.class);
    
    public AbstractLoadBalancerAwareClient() {
        super();
    }
    
    /**
     * Delegate to {@link #initWithNiwsConfig(IClientConfig)}
     * @param clientConfig
     */
    public AbstractLoadBalancerAwareClient(IClientConfig clientConfig) {
        super(clientConfig);        
    }
    
    /**
     * Determine if an exception should contribute to circuit breaker trip. If such exceptions happen consecutively
     * on a server, it will be deemed as circuit breaker tripped and enter into a time out when it will be
     * skipped by the {@link AvailabilityFilteringRule}, which is the default rule for load balancers.
     */
    protected abstract boolean isCircuitBreakerException(Throwable e);
        
    /**
     * Determine if operation can be retried if an exception is thrown. For example, connect 
     * timeout related exceptions
     * are typically retriable.
     * 
     */
    protected abstract boolean isRetriableException(Throwable e);
    
    /**
     * Execute the request on single server after the final URI is calculated. This method takes care of
     * retries and update server stats.
     *  
     */
    protected T executeOnSingleServer(S request) throws ClientException {
        boolean done = false;
        int retries = 0;

        boolean retryOkayOnOperation = okToRetryOnAllOperations;
        if (request.isRetriable()) {
        	retryOkayOnOperation = true;
        }
        int numRetries =  maxAutoRetries;
        URI uri = request.getUri();
        Server server = new Server(uri.getHost(), uri.getPort());
        ServerStats serverStats = null;
        ILoadBalancer lb = this.getLoadBalancer();
        if (lb instanceof AbstractLoadBalancer){
            LoadBalancerStats lbStats = ((AbstractLoadBalancer) lb).getLoadBalancerStats();
            serverStats = lbStats.getSingleServerStat(server);
        }
        IClientConfig overriddenClientConfig = request.getOverrideConfig();
        if (overriddenClientConfig!=null){
            try {
                numRetries = Integer.parseInt(""+overriddenClientConfig.getProperty(CommonClientConfigKey.MaxAutoRetries,maxAutoRetries));
            } catch (Exception e) {
                logger.warn("Invalid maxRetries requested for RestClient:" + this.clientName);
            }
        }
        
        T response = null;
        Throwable lastException = null;
        Timer tracer = getExecuteTracer();
        if (tracer == null) {
           tracer = Monitors.newTimer(this.getClass().getName() + "_ExecutionTimer", TimeUnit.MILLISECONDS);
        }
        do {            
            noteOpenConnection(serverStats, request);
            Stopwatch w = tracer.start();
            try {
                response = execute(request);        
                done = true;
            } catch (Throwable e) {
                if (serverStats != null) {
                    serverStats.addToFailureCount();
                }
                lastException = e;
                if (isCircuitBreakerException(e) && serverStats != null) {
                    serverStats.incrementSuccessiveConnectionFailureCount();
                }
                boolean shouldRetry = retryOkayOnOperation && numRetries >= 0 && isRetriableException(e);
                if (shouldRetry) {
                    retries++;
                    if (!handleSameServerRetry(uri, retries, numRetries, e)) {
                        throw new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
                                "NUMBEROFRETRIESEXEEDED :" + numRetries + " retries, while making a RestClient call for:" + uri,
                                e !=null? e: new RuntimeException());
                    }
                } else {
                    ClientException niwsClientException = generateNIWSException(uri.toString(), e);
                    throw niwsClientException;
                }
            } finally {
                w.stop();
                noteRequestCompletion(serverStats, request, response, lastException, w.getDuration(TimeUnit.MILLISECONDS));
            }
        } while (!done); 
        return response;
    }
    
    /**
     * This method should be used when the caller wants to dispatch the request to a server chosen by
     * the load balancer, instead of specifying the server in the request's URI. 
     * It calculates the final URI by calling {@link #computeFinalUriWithLoadBalancer(ClientRequest)}
     * and then calls {@link #execute(ClientRequest)}.
     * 
     * @param request request to be dispatched to a server chosen by the load balancer. The URI can be a partial
     * URI which does not contain the host name or the protocol.
     */
    public T executeWithLoadBalancer(S request) throws ClientException {
        int retries = 0;
        boolean done = false;

        final boolean retryOkayOnOperation = isRetriable(request);

        final int numRetriesNextServer = getRetriesNextServer(request.getOverrideConfig()); 

        T response = null;

        do {
            try {
                S resolved = computeFinalUriWithLoadBalancer(request);
                response = executeOnSingleServer(resolved);
                done = true;
            } catch (Exception e) {      
                boolean shouldRetry = false;
                if (e instanceof ClientException) {
                    // we dont want to retry for PUT/POST and DELETE, we can for GET
                    shouldRetry = retryOkayOnOperation && numRetriesNextServer > 0;
                }
                if (shouldRetry) {
                    retries++;
                    if (retries > numRetriesNextServer) {
                        throw new ClientException(
                                ClientException.ErrorType.NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED,
                                "NUMBER_OF_RETRIES_NEXTSERVER_EXCEEDED :"
                                + numRetriesNextServer
                                + " retries, while making a RestClient call for:"
                                + request.getUri() + ":" +  getDeepestCause(e).getMessage(), e);
                    }
                    logger.error("Exception while executing request which is deemed retry-able, retrying ..., Next Server Retry Attempt#:"
                            + retries
                            + ", URI tried:"
                            + request.getUri());
                } else {
                    if (e instanceof ClientException) {
                        throw (ClientException) e;
                    } else {
                        throw new ClientException(
                                ClientException.ErrorType.GENERAL,
                                "Unable to execute request for URI:" + request.getUri(),
                                e);
                    }
                }
            } 
        } while (!done);
        return response;
    }
}

