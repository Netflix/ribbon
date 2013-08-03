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
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
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
import com.netflix.util.Pair;

/**
 * Abstract class that provides the integration of client with load balancers.
 * 
 * @author awang
 *
 */
public abstract class AbstractLoadBalancerAwareClient<S extends ClientRequest, T extends IResponse> implements IClient<S, T>, IClientConfigAware {    
    
    private static final Logger logger = LoggerFactory.getLogger(AbstractLoadBalancerAwareClient.class);

    private String clientName;          
    
    private String vipAddresses;
    
    private int maxAutoRetriesNextServer = DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER;
    private int maxAutoRetries = DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES;


    boolean okToRetryOnAllOperations = DefaultClientConfigImpl.DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS.booleanValue();
        
    private ILoadBalancer lb;
    private Timer tracer;

    
    public AbstractLoadBalancerAwareClient() {  
    }
    
    /**
     * Set necessary parameters from client configuration and register with Servo monitors.
     */
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        if (clientConfig == null) {
            return;    
        }
        vipAddresses = clientConfig.resolveDeploymentContextbasedVipAddresses();
        clientName = clientConfig.getClientName();
        if (clientName == null) {
        	clientName = "default";
        }
        try {
            maxAutoRetries = Integer.parseInt(clientConfig.getProperty(
            		CommonClientConfigKey.MaxAutoRetries,maxAutoRetries).toString());
        } catch (Exception e) {
            logger.warn("Invalid maxRetries set for client:" + clientName);
        }
        try {
            maxAutoRetriesNextServer = Integer.parseInt(clientConfig.getProperty(
            		CommonClientConfigKey.MaxAutoRetriesNextServer,maxAutoRetriesNextServer).toString());
        } catch (Exception e) {
            logger.warn("Invalid maxRetriesNextServer set for client:" + clientName);
        }
        
        try {
            Boolean bOkToRetryOnAllOperations = Boolean.valueOf(clientConfig.getProperty(CommonClientConfigKey.OkToRetryOnAllOperations,
                    okToRetryOnAllOperations).toString());
            okToRetryOnAllOperations = bOkToRetryOnAllOperations.booleanValue();
        } catch (Exception e) {
            logger.warn("Invalid OkToRetryOnAllOperations set for client:" + clientName);
        }
        tracer = Monitors.newTimer(clientName + "_OperationTimer", TimeUnit.MILLISECONDS);
        Monitors.registerObject("Client_" + clientName, this);
    }
    
    /**
     * Delegate to {@link #initWithNiwsConfig(IClientConfig)}
     * @param clientConfig
     */
    public AbstractLoadBalancerAwareClient(IClientConfig clientConfig) {
        initWithNiwsConfig(clientConfig);        
    }
    
    public final String getClientName() {
        return clientName;
    }
        
    public ILoadBalancer getLoadBalancer() {
        return lb;    
    }
        
    public void setLoadBalancer(ILoadBalancer lb) {
        this.lb = lb;
    }

    private Throwable getDeepestCause(Throwable e) {
        if(e != null) {
            int infiniteLoopPreventionCounter = 10;
            while (e.getCause() != null && infiniteLoopPreventionCounter > 0) {
                infiniteLoopPreventionCounter--;
                e = e.getCause();
            }
        }
        return e;
    }

    /**
     * Determine if an exception should contribute to circuit breaker trip. If such exceptions happen consecutively
     * on a server, it will be deemed as circuit breaker tripped and enter into a time out when it will be
     * skipped by the {@link AvailabilityFilteringRule}, which is the default rule for load balancers.
     */
    protected abstract boolean isCircuitBreakerException(Exception e);
        
    /**
     * Determine if operation can be retried if an exception is thrown. For example, connect 
     * timeout related exceptions
     * are typically retriable.
     * 
     */
    protected abstract boolean isRetriableException(Exception e);
    
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
        Exception lastException = null;
        if (tracer == null) {
           tracer = Monitors.newTimer(this.getClass().getName() + "_ExecutionTimer", TimeUnit.MILLISECONDS);
        }
        do {            
            noteOpenConnection(serverStats, request);
            Stopwatch w = tracer.start();
            try {
                response = execute(request);        
                done = true;
            } catch (Exception e) {
                if (serverStats != null) {
                    serverStats.addToFailureCount();
                }
                lastException = e;
                if (isCircuitBreakerException(e) && serverStats != null) {
                    serverStats.incrementSuccessiveConnectionFailureCount();
                }
                boolean shouldRetry = retryOkayOnOperation && numRetries >= 0 && isRetriableException(e);
                if (shouldRetry) {
                    retries = handleRetry(uri.toString(), retries, numRetries, e);
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
    
    private boolean isPresentAsCause(Throwable throwableToSearchIn,
            Class<? extends Throwable> throwableToSearchFor) {
        return isPresentAsCauseHelper(throwableToSearchIn, throwableToSearchFor) != null;
    }

    private Throwable isPresentAsCauseHelper(Throwable throwableToSearchIn,
            Class<? extends Throwable> throwableToSearchFor) {
        int infiniteLoopPreventionCounter = 10;
        while (throwableToSearchIn != null && infiniteLoopPreventionCounter > 0) {
            infiniteLoopPreventionCounter--;
            if (throwableToSearchIn.getClass().isAssignableFrom(
                    throwableToSearchFor)) {
                return throwableToSearchIn;
            } else {
                throwableToSearchIn = throwableToSearchIn.getCause();
            }
        }
        return null;
    }

    private ClientException generateNIWSException(String uri, Exception e){
        ClientException niwsClientException;
        if (isPresentAsCause(e, java.net.SocketTimeoutException.class)) {
            niwsClientException = generateTimeoutNIWSException(uri, e);
        }else if (e.getCause() instanceof java.net.UnknownHostException){
            niwsClientException = new ClientException(
                    ClientException.ErrorType.UNKNOWN_HOST_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri,
                    e);
        }else if (e.getCause() instanceof java.net.ConnectException){
            niwsClientException = new ClientException(
                    ClientException.ErrorType.CONNECT_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri,
                    e);
        }else if (e.getCause() instanceof java.net.NoRouteToHostException){
            niwsClientException = new ClientException(
                    ClientException.ErrorType.NO_ROUTE_TO_HOST_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri,
                    e);
        }else if (e instanceof ClientException){
            niwsClientException = (ClientException)e;
        }else {
            niwsClientException = new ClientException(
                ClientException.ErrorType.GENERAL,
                "Unable to execute RestClient request for URI:" + uri,
                e);
        }
        return niwsClientException;
    }

    private boolean isPresentAsCause(Throwable throwableToSearchIn,
            Class<? extends Throwable> throwableToSearchFor, String messageSubStringToSearchFor) {
        Throwable throwableFound = isPresentAsCauseHelper(throwableToSearchIn, throwableToSearchFor);
        if(throwableFound != null) {
            return throwableFound.getMessage().contains(messageSubStringToSearchFor);
        }
        return false;
    }
    private ClientException generateTimeoutNIWSException(String uri, Exception e){
        ClientException niwsClientException;
        if (isPresentAsCause(e, java.net.SocketTimeoutException.class,
                "Read timed out")) {
            niwsClientException = new ClientException(
                    ClientException.ErrorType.READ_TIMEOUT_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri + ":"
                            + getDeepestCause(e).getMessage(), e);
        } else {
            niwsClientException = new ClientException(
                    ClientException.ErrorType.SOCKET_TIMEOUT_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri + ":"
                            + getDeepestCause(e).getMessage(), e);
        }
        return niwsClientException;
    }

    private int handleRetry(String uri, int retries, int numRetries,
            Exception e) throws ClientException {
        retries++;

        if (retries > numRetries) {
            throw new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
                    "NUMBEROFRETRIESEXEEDED :" + numRetries + " retries, while making a RestClient call for:" + uri,
                    e !=null? e: new RuntimeException());
        }
        logger.error("Exception while executing request which is deemed retry-able, retrying ..., SAME Server Retry Attempt#:" +
                retries +
                ", URI:" +
                uri);
        try {
            Thread.sleep((int) Math.pow(2.0, retries) * 100); 
        } catch (InterruptedException ex) {
        }
        return retries;
    }

    /**
     * This is called after a response is received or an exception is thrown from the {@link #execute(ClientRequest)}
     * to update related stats.  
     */
    protected void noteRequestCompletion(ServerStats stats, S task, IResponse response, Exception e, long responseTime) {        
        try {
            if (stats != null) {
                stats.decrementActiveRequestsCount();
                stats.incrementNumRequests();
                stats.noteResponseTime(responseTime);
                if (response != null) {
                    stats.clearSuccessiveConnectionFailureCount();                    
                }
            }            
        } catch (Throwable ex) {
            logger.error("Unexpected exception", ex);
        }            
    }
        
    /**
     * This method is called after a response (either success or not) is received to update certain stats.
     */
    protected void noteResponseReceived(ServerStats stats, T task, IResponse response) {
        if (stats == null) {
            return;
        }
        try {
            stats.clearSuccessiveConnectionFailureCount();
        } catch (Throwable  e) {
            logger.info("Unable to note Server Stats:", e);
        }        
    }
       
    /**
     * Called just before {@link #execute(ClientRequest)} call.
     */
    protected void noteOpenConnection(ServerStats serverStats, S task) {
        if (serverStats == null) {
            return;
        }
        try {
            serverStats.incrementActiveRequestsCount();
        } catch (Throwable e) {
            logger.info("Unable to note Server Stats:", e);
        }
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
        boolean retryOkayOnOperation = okToRetryOnAllOperations;

        retryOkayOnOperation = request.isRetriable();
        // Is it okay to retry for this particular operation?

        // see if maxRetries has been overriden
        int numRetries = maxAutoRetriesNextServer;
        IClientConfig overriddenClientConfig = request.getOverrideConfig();
        if (overriddenClientConfig != null) {
            try {
                numRetries = Integer.parseInt(""+overriddenClientConfig.getProperty(
                        CommonClientConfigKey.MaxAutoRetriesNextServer,
                        maxAutoRetriesNextServer));
            } catch (Exception e) {
                logger
                .warn("Invalid maxAutoRetriesNextServer requested for RestClient:"
                        + this.getClientName());
            }
            try {
                // Retry operation can be forcefully turned on or off for this particular request
                Boolean requestSpecificRetryOn = Boolean.valueOf(""+
                        overriddenClientConfig.getProperty(CommonClientConfigKey.RequestSpecificRetryOn,
                        "false"));
                retryOkayOnOperation = requestSpecificRetryOn.booleanValue();
            } catch (Exception e) {
                logger.warn("Invalid RequestSpecificRetryOn set for RestClient:" + this.getClientName());
            }
        }

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
                    shouldRetry = retryOkayOnOperation && numRetries>0;
                }
                if (shouldRetry) {
                    retries++;
                    if (retries > numRetries) {
                        throw new ClientException(
                                ClientException.ErrorType.NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED,
                                "NUMBER_OF_RETRIES_NEXTSERVER_EXCEEDED :"
                                + numRetries
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
      
    /**
     * Derive scheme and port from a partial URI. For example, for HTTP based client, the URI with 
     * only path "/" should return "http" and 80, whereas the URI constructed with scheme "https" and
     * path "/" should return
     * "https" and 443. This method is called by {@link #computeFinalUriWithLoadBalancer(ClientRequest)}
     * to get the complete executable URI.
     * 
     */
    protected abstract Pair<String, Integer> deriveSchemeAndPortFromPartialUri(S task);
    
    /**
     * Get the default port from the vip address.
     * 
     * @deprecated replaced by {@link #getDefaultPortFromScheme(String)}
     */
    protected abstract int getDefaultPort();
    
    
    /**
     * Get the default port of the target server given the scheme of vip address if it is available. 
     * Subclass should override it to provider protocol specific default port number if any.
     * 
     * @param scheme from the vip address. null if not present.
     * @return 80 if scheme is http, 443 if scheme is https, -1 else.
     */
    protected int getDefaultPortFromScheme(String scheme) {
        if (scheme == null) {
            return -1;
        }
        if (scheme.equals("http")) {
            return 80;
        } else if (scheme.equals("https")) {
            return 443;
        } else {
            return -1;
        }
    }

        
    /**
     * Derive the host and port from virtual address if virtual address is indeed contains the actual host 
     * and port of the server. This is the final resort to compute the final URI in {@link #computeFinalUriWithLoadBalancer(ClientRequest)}
     * if there is no load balancer available and the request URI is incomplete. Sub classes can override this method
     * to be more accurate or throws ClientException if it does not want to support virtual address to be the
     * same as physical server address.
     * <p>
     *  The virtual address is used by certain load balancers to filter the servers of the same function 
     *  to form the server pool. 
     *  
     */
    protected  Pair<String, Integer> deriveHostAndPortFromVipAddress(String vipAddress) 
    		throws URISyntaxException, ClientException {
        Pair<String, Integer> hostAndPort = new Pair<String, Integer>(null, -1);
        URI uri = new URI(vipAddress);
        String scheme = uri.getScheme();
        if (scheme == null) {
        	uri = new URI("http://" + vipAddress);
        }
        String host = uri.getHost();
        if (host == null) {
        	throw new ClientException("Unable to derive host/port from vip address " + vipAddress);
        }
        int port = uri.getPort();
        if (port < 0) {
        	port = getDefaultPortFromScheme(scheme);
        }
        if (port < 0) {
        	throw new ClientException("Unable to derive host/port from vip address " + vipAddress);
        }
        hostAndPort.setFirst(host);
        hostAndPort.setSecond(port);
        return hostAndPort;
    }
    
    private boolean isVipRecognized(String vipEmbeddedInUri) {
        if (vipEmbeddedInUri == null) {
            return false;
        }
        if (vipAddresses == null) {
            return false;
        }
        String[] addresses = vipAddresses.split(",");
        for (String address: addresses) {
            if (vipEmbeddedInUri.equalsIgnoreCase(address.trim())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Compute the final URI from a partial URI in the request. The following steps are performed:
     * 
     * <li> if host is missing and there is a load balancer, get the host/port from server chosen from load balancer
     * <li> if host is missing and there is no load balancer, try to derive host/port from virtual address set with the client
     * <li> if host is present and the authority part of the URI is a virtual address set for the client, 
     * and there is a load balancer, get the host/port from server chosen from load balancer
     * <li> if host is present but none of the above applies, interpret the host as the actual physical address
     * <li> if host is missing but none of the above applies, throws ClientException
     * 
     * @param original Original URI passed from caller
     * @return new request with the final URI  
     */
    @SuppressWarnings("unchecked")
    protected S computeFinalUriWithLoadBalancer(S original) throws ClientException{
        URI newURI;
        URI theUrl = original.getUri();

        if (theUrl == null){
            throw new ClientException(ClientException.ErrorType.GENERAL, "NULL URL passed in");
        }

        String host = theUrl.getHost();
        Pair<String, Integer> schemeAndPort = deriveSchemeAndPortFromPartialUri(original);
        String scheme = schemeAndPort.first();
        int port = schemeAndPort.second();
        // Various Supported Cases
        // The loadbalancer to use and the instances it has is based on how it was registered
        // In each of these cases, the client might come in using Full Url or Partial URL
        ILoadBalancer lb = getLoadBalancer();
        Object loadBalancerKey = original.getLoadBalancerKey();
        if (host == null){
            // Partial URL Case
            // well we have to just get the right instances from lb - or we fall back
            if (lb != null){
                Server svc = lb.chooseServer(loadBalancerKey);
                if (svc == null){
                    throw new ClientException(ClientException.ErrorType.GENERAL,
                            "LoadBalancer returned null Server for :"
                            + clientName);
                }
                host = svc.getHost();
                port = svc.getPort();
                if (host == null){
                    throw new ClientException(ClientException.ErrorType.GENERAL,
                            "Invalid Server for :" + svc);
                }
                if (logger.isDebugEnabled()){
                    logger.debug(clientName + " using LB returned Server:" + svc + "for request:" + theUrl);
                }
            } else {
                // No Full URL - and we dont have a LoadBalancer registered to
                // obtain a server
                // if we have a vipAddress that came with the registration, we
                // can use that else we
                // bail out
                if (vipAddresses != null && vipAddresses.contains(",")) {
                    throw new ClientException(
                            ClientException.ErrorType.GENERAL,
                            this.clientName
                                    + "Partial URI of ("
                                    + theUrl
                                    + ") has been sent in to RestClient (with no LB) to be executed."
                                    + " Also, there are multiple vipAddresses and hence RestClient cant pick"
                                    + "one vipAddress to complete this partial uri");
                } else if (vipAddresses != null) {
                    try {
                        Pair<String,Integer> hostAndPort = deriveHostAndPortFromVipAddress(vipAddresses);
                        host = hostAndPort.first();
                        port = hostAndPort.second();
                    } catch (URISyntaxException e) {
                        throw new ClientException(
                                ClientException.ErrorType.GENERAL,
                                this.clientName
                                        + "Partial URI of ("
                                        + theUrl
                                        + ") has been sent in to RestClient (with no LB) to be executed."
                                        + " Also, the configured/registered vipAddress is unparseable (to determine host and port)");
                    }
                }else{
                    throw new ClientException(
                            ClientException.ErrorType.GENERAL,
                            this.clientName
                                    + " has no LoadBalancer registered and passed in a partial URL request (with no host:port)."
                                    + " Also has no vipAddress registered");
                }
            }
        } else {
            // Full URL Case
            // This could either be a vipAddress or a hostAndPort or a real DNS
            // if vipAddress or hostAndPort, we just have to consult the loadbalancer
            // but if it does not return a server, we should just proceed anyways
            // and assume its a DNS
            // For restClients registered using a vipAddress AND executing a request
            // by passing in the full URL (including host and port), we should only
            // consult lb IFF the URL passed is registered as vipAddress in Discovery
            boolean shouldInterpretAsVip = false;

            if (lb != null) {
                shouldInterpretAsVip = isVipRecognized(original.getUri().getAuthority());
            }
            if (shouldInterpretAsVip) {
                Server svc = lb.chooseServer(loadBalancerKey);
                if (svc != null){
                    host = svc.getHost();
                    port = svc.getPort();
                    if (host == null){
                        throw new ClientException(ClientException.ErrorType.GENERAL,
                                "Invalid Server for :" + svc);
                    }
                    if (logger.isDebugEnabled()){
                        logger.debug("using LB returned Server:" + svc + "for request:" + theUrl);
                    }
                }else{
                    // just fall back as real DNS
                    if (logger.isDebugEnabled()){
                        logger.debug(host + ":" + port + " assumed to be a valid VIP address or exists in the DNS");
                    }
                }
            } else {
             // consult LB to obtain vipAddress backed instance given full URL
                //Full URL execute request - where url!=vipAddress
               if (logger.isDebugEnabled()){
                   logger.debug("Using full URL passed in by caller (not using LB/Discovery):" + theUrl);
               }
            }
        }
        // end of creating final URL
        if (host == null){
            throw new ClientException(ClientException.ErrorType.GENERAL,"Request contains no HOST to talk to");
        }
        // just verify that at this point we have a full URL

        try {
            String urlPath = "";
            if (theUrl.getRawPath() != null && theUrl.getRawPath().startsWith("/")) {
                urlPath = theUrl.getRawPath();
            } else {
                urlPath = "/" + theUrl.getRawPath();
            }
            
            newURI = new URI(scheme, theUrl.getUserInfo(), host, port, urlPath, theUrl.getQuery(), theUrl.getFragment());
            return (S) original.replaceUri(newURI);            
        } catch (URISyntaxException e) {
            throw new ClientException(ClientException.ErrorType.GENERAL, e.getMessage());
        }
    }
}

