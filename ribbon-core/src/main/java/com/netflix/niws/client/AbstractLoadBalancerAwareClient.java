package com.netflix.niws.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.niws.client.NiwsClientConfig.NiwsClientConfigKey;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;
import com.netflix.util.Pair;

public abstract class AbstractLoadBalancerAwareClient<S extends ClientRequest, T extends IResponse> implements IClient<S, T>, NiwsClientConfigAware {    
    
    private static final Logger logger = LoggerFactory.getLogger(AbstractLoadBalancerAwareClient.class);

    private String clientName;          
    
    private String vipAddresses;
    
    private int maxAutoRetriesNextServer = NiwsClientConfig.DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER;
    private int maxAutoRetries = NiwsClientConfig.DEFAULT_MAX_AUTO_RETRIES;


    boolean okToRetryOnAllOperations = NiwsClientConfig.DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS.booleanValue();
    
    AtomicLong numRetriesOnNextServer = new AtomicLong(0);
    
    AtomicLong numRetriesOnNextServerExceeded = new AtomicLong(0);
    
    private ILoadBalancer lb;
    private Timer tracer;

    
    public AbstractLoadBalancerAwareClient() {  
    }
    
    @Override
    public void initWithNiwsConfig(NiwsClientConfig clientConfig) {
        if (clientConfig == null) {
            return;    
        }
        vipAddresses = clientConfig.resolveDeploymentContextbasedVipAddresses();
        clientName = clientConfig.getClientName();
        try {
            maxAutoRetries = Integer.parseInt(""+ clientConfig.getProperty(NiwsClientConfigKey.MaxAutoRetries,maxAutoRetries));
        } catch (Exception e) {
            logger.warn("Invalid maxRetries set for client:" + clientName);
        }
        try {
            maxAutoRetriesNextServer = Integer.parseInt(""+ clientConfig.getProperty(NiwsClientConfigKey.MaxAutoRetriesNextServer,maxAutoRetriesNextServer));
        } catch (Exception e) {
            logger.warn("Invalid maxRetriesNextServer set for client:" + clientName);
        }
        
        try {
            Boolean bOkToRetryOnAllOperations = Boolean.valueOf(""+clientConfig.getProperty(NiwsClientConfigKey.OkToRetryOnAllOperations,
                    Boolean.valueOf(okToRetryOnAllOperations)));
            okToRetryOnAllOperations = bOkToRetryOnAllOperations.booleanValue();
        } catch (Exception e) {
            logger.warn("Invalid OkToRetryOnAllOperations set for client:" + clientName);
        }
        tracer = Monitors.newTimer(clientName + "_OperationTimer", TimeUnit.MILLISECONDS);
    }
    
    public AbstractLoadBalancerAwareClient(NiwsClientConfig clientConfig) {
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

    protected abstract boolean isCircuitBreakerException(Exception e);
        
    protected abstract boolean isRetriableException(Exception e);
    
    protected T executeOnSingleServer(S request) throws NIWSClientException {
        boolean done = false;
        int retries = 0;

        boolean retryOkayOnOperation = okToRetryOnAllOperations;
        
        int numRetries =  maxAutoRetries;
        URI uri = request.getUri();
        Server server = new Server(uri.getHost(), uri.getPort());
        ServerStats serverStats = null;
        ILoadBalancer lb = this.getLoadBalancer();
        if (lb instanceof AbstractLoadBalancer){
            LoadBalancerStats lbStats = ((AbstractLoadBalancer) lb).getLoadBalancerStats();
            serverStats = lbStats.getSingleServerStat(server);
        }
        NiwsClientConfig overriddenClientConfig = request.getOverrideConfig();
        if (overriddenClientConfig!=null){
            try {
                numRetries = Integer.parseInt(""+overriddenClientConfig.getProperty(NiwsClientConfigKey.MaxAutoRetries,maxAutoRetries));
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
                lastException = e;
                if (isCircuitBreakerException(e) && serverStats != null) {
                    serverStats.incrementSuccessiveConnectionFailureCount();
                }
                boolean shouldRetry = retryOkayOnOperation && numRetries >= 0 && isRetriableException(e);
                if (shouldRetry) {
                    retries = handleRetry(uri.toString(), retries, numRetries, e);
                } else {
                    NIWSClientException niwsClientException = generateNIWSException(uri.toString(), e);
                    throw niwsClientException;
                }
            } finally {
                w.stop();
                noteRequestCompletion(serverStats, request, response, lastException, w.getDuration());
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

    private NIWSClientException generateNIWSException(String uri, Exception e){
        NIWSClientException niwsClientException;
        if (isPresentAsCause(e, java.net.SocketTimeoutException.class)) {
            niwsClientException = generateTimeoutNIWSException(uri, e);
        }else if (e.getCause() instanceof java.net.UnknownHostException){
            niwsClientException = new NIWSClientException(
                    NIWSClientException.ErrorType.UNKNOWN_HOST_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri,
                    e);
        }else if (e.getCause() instanceof java.net.ConnectException){
            niwsClientException = new NIWSClientException(
                    NIWSClientException.ErrorType.CONNECT_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri,
                    e);
        }else if (e.getCause() instanceof java.net.NoRouteToHostException){
            niwsClientException = new NIWSClientException(
                    NIWSClientException.ErrorType.NO_ROUTE_TO_HOST_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri,
                    e);
        }else if (e instanceof NIWSClientException){
            niwsClientException = (NIWSClientException)e;
        }else {
            niwsClientException = new NIWSClientException(
                NIWSClientException.ErrorType.GENERAL,
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
    private NIWSClientException generateTimeoutNIWSException(String uri, Exception e){
        NIWSClientException niwsClientException;
        if (isPresentAsCause(e, java.net.SocketTimeoutException.class,
                "Read timed out")) {
            niwsClientException = new NIWSClientException(
                    NIWSClientException.ErrorType.READ_TIMEOUT_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri + ":"
                            + getDeepestCause(e).getMessage(), e);
        } else {
            niwsClientException = new NIWSClientException(
                    NIWSClientException.ErrorType.SOCKET_TIMEOUT_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri + ":"
                            + getDeepestCause(e).getMessage(), e);
        }
        return niwsClientException;
    }

    private int handleRetry(String uri, int retries, int numRetries,
            Exception e) throws NIWSClientException {
        retries++;

        if (retries > numRetries) {
            throw new NIWSClientException(NIWSClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
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
        
    protected void noteConnectException(ServerStats serverStats) {
        if (serverStats == null) {
            return;
        }
        try {
            serverStats.incrementSuccessiveConnectionFailureCount();
        } catch (Throwable e) {
            logger.info("Unable to note Server Stats:", e);
        }        
    }

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

    public T executeWithLoadBalancer(S task) throws NIWSClientException {
        int retries = 0;
        boolean done = false;
        boolean retryOkayOnOperation = okToRetryOnAllOperations;

        retryOkayOnOperation = task.isRetriable();
        // Is it okay to retry for this particular operation?

        // see if maxRetries has been overriden
        int numRetries = maxAutoRetriesNextServer;
        NiwsClientConfig overriddenClientConfig = task.getOverrideConfig();
        if (overriddenClientConfig != null) {
            try {
                numRetries = Integer.parseInt(""+overriddenClientConfig.getProperty(
                        NiwsClientConfigKey.MaxAutoRetriesNextServer,
                        maxAutoRetriesNextServer));
            } catch (Exception e) {
                logger
                .warn("Invalid maxAutoRetriesNextServer requested for RestClient:"
                        + this.getClientName());
            }
            try {
                // Retry operation can be forcefully turned on or off for this particular request
                Boolean requestSpecificRetryOn = Boolean.valueOf(""+
                        overriddenClientConfig.getProperty(NiwsClientConfigKey.RequestSpecificRetryOn,
                        "false"));
                retryOkayOnOperation = requestSpecificRetryOn.booleanValue();
            } catch (Exception e) {
                logger.warn("Invalid RequestSpecificRetryOn set for RestClient:" + this.getClientName());
            }
        }

        T response = null;

        do {
            try {
                S resolved = computeFinalUriWithLoadBalancer(task);
                response = executeOnSingleServer(resolved);
                done = true;
            } catch (Exception e) {      
                boolean shouldRetry = false;
                if (e instanceof NIWSClientException) {
                    // we dont want to retry for PUT/POST and DELETE, we can for GET
                    shouldRetry = retryOkayOnOperation && numRetries>0;
                }
                if (shouldRetry) {
                    retries++;
                    if (retries > numRetries) {
                        throw new NIWSClientException(
                                NIWSClientException.ErrorType.NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED,
                                "NUMBER_OF_RETRIES_NEXTSERVER_EXCEEDED :"
                                + numRetries
                                + " retries, while making a RestClient call for:"
                                + task.getUri() + ":" +  getDeepestCause(e).getMessage(), e);
                    }
                    logger.error("Exception while executing request which is deemed retry-able, retrying ..., Next Server Retry Attempt#:"
                            + retries
                            + ", URI tried:"
                            + task.getUri());
                } else {
                    if (e instanceof NIWSClientException) {
                        throw (NIWSClientException) e;
                    } else {
                        throw new NIWSClientException(
                                NIWSClientException.ErrorType.GENERAL,
                                "Unable to execute request for URI:" + task.getUri(),
                                e);
                    }
                }
            } 
        } while (!done);
        return response;
    }
        
    protected abstract Pair<String, Integer> deriveSchemeAndPortFromPartialUri(S task);
    
    protected abstract int getDefaultPort();
        
    protected  Pair<String, Integer> deriveHostAndPortFromVipAddress(String vipAddress) 
    		throws URISyntaxException, NIWSClientException {
        Pair<String, Integer> hostAndPort = new Pair<String, Integer>(null, -1);
        URI uri = new URI(vipAddress);
        String scheme = uri.getScheme();
        if (scheme == null) {
        	uri = new URI("http://" + vipAddress);
        }
        String host = uri.getHost();
        if (host == null) {
        	throw new NIWSClientException("Unable to derive host/port from vip address " + vipAddress);
        }
        int port = uri.getPort();
        if (port < 0) {
        	port = getDefaultPort();
        }
        if (port < 0) {
        	throw new NIWSClientException("Unable to derive host/port from vip address " + vipAddress);
        }
        hostAndPort.setFirst(host);
        hostAndPort.setSecond(port);
        return hostAndPort;
    }
    
    protected boolean isVipRecognized(String vipEmbeddedInUri) {
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
    
    protected S computeFinalUriWithLoadBalancer(S original) throws NIWSClientException{
        URI newURI;
        URI theUrl = original.getUri();

        if (theUrl == null){
            throw new NIWSClientException(NIWSClientException.ErrorType.GENERAL, "NULL URL passed in");
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
                    throw new NIWSClientException(NIWSClientException.ErrorType.GENERAL,
                            "LoadBalancer returned null Server for :"
                            + clientName);
                }
                host = svc.getHost();
                port = svc.getPort();
                if (host == null){
                    throw new NIWSClientException(NIWSClientException.ErrorType.GENERAL,
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
                    throw new NIWSClientException(
                            NIWSClientException.ErrorType.GENERAL,
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
                        throw new NIWSClientException(
                                NIWSClientException.ErrorType.GENERAL,
                                this.clientName
                                        + "Partial URI of ("
                                        + theUrl
                                        + ") has been sent in to RestClient (with no LB) to be executed."
                                        + " Also, the configured/registered vipAddress is unparseable (to determine host and port)");
                    }
                }else{
                    throw new NIWSClientException(
                            NIWSClientException.ErrorType.GENERAL,
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
                        throw new NIWSClientException(NIWSClientException.ErrorType.GENERAL,
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
            throw new NIWSClientException(NIWSClientException.ErrorType.GENERAL,"Request contains no HOST to talk to");
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
            throw new NIWSClientException(NIWSClientException.ErrorType.GENERAL, e.getMessage());
        }
    }
}

