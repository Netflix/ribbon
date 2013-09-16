package com.netflix.client;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;

public class AsyncLoadBalancingClient<Request extends ClientRequest, Response extends ResponseWithTypedEntity>
        extends LoadBalancerContext implements AsyncClient<Request, Response> {
    
    private AsyncClient<Request, Response> asyncClient;
    private static final Logger logger = LoggerFactory.getLogger(AsyncLoadBalancingClient.class);
    private Timer tracer;


    public AsyncLoadBalancingClient(AsyncClient<Request, Response> asyncClient) {
        super();
        this.asyncClient = asyncClient;
    }

    protected AsyncLoadBalancingClient() {
    }

    @Override
    public void execute(final Request request, final ResponseCallback<Response> callback)
            throws ClientException {
        final AtomicInteger retries = new AtomicInteger(0);
        final boolean retryOkayOnOperation = isRetriable(request);

        final int numRetriesNextServer = getRetriesNextServer(request.getOverrideConfig()); 
        Request resolved = computeFinalUriWithLoadBalancer(request);
        asyncExecuteOnSingleServer(resolved, new ResponseCallback<Response>() {

            @Override
            public void onResponseReceived(Response response) {
                callback.onResponseReceived(response);
            }

            @Override
            public void onException(Throwable e) {
                boolean shouldRetry = false;
                if (e instanceof ClientException) {
                    // we dont want to retry for PUT/POST and DELETE, we can for GET
                    shouldRetry = retryOkayOnOperation && numRetriesNextServer > 0;
                }
                if (shouldRetry) {
                    if (retries.incrementAndGet() > numRetriesNextServer) {
                        callback.onException(new ClientException(
                                ClientException.ErrorType.NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED,
                                "NUMBER_OF_RETRIES_NEXTSERVER_EXCEEDED :"
                                + numRetriesNextServer
                                + " retries, while making a RestClient call for:"
                                + request.getUri() + ":" +  getDeepestCause(e).getMessage(), e));
                    }
                    logger.error("Exception while executing request which is deemed retry-able, retrying ..., Next Server Retry Attempt#:"
                            + retries
                            + ", URI tried:"
                            + request.getUri());
                    try {
                        asyncExecuteOnSingleServer(computeFinalUriWithLoadBalancer(request), this);
                    } catch (ClientException e1) {
                        callback.onException(e1);
                    }
                } else {
                    if (e instanceof ClientException) {
                        callback.onException(e);
                    } else {
                        callback.onException(new ClientException(
                                ClientException.ErrorType.GENERAL,
                                "Unable to execute request for URI:" + request.getUri(),
                                e));
                    }
                }
            }
            
        });
    }

    
    
    private Timer getExecuteTracer() {
        if (tracer == null) {
            tracer = Monitors.newTimer(this.getClientName() + "_ExecutionTimer", TimeUnit.MILLISECONDS);
        }
        return tracer;
    }
    
    /**
     * Execute the request on single server after the final URI is calculated. This method takes care of
     * retries and update server stats.
     * @throws ClientException 
     *  
     */
    protected void asyncExecuteOnSingleServer(final Request request, final ResponseCallback<Response> callback) throws ClientException {
        final AtomicInteger retries = new AtomicInteger(0);

        final boolean retryOkayOnOperation = request.isRetriable()? true: okToRetryOnAllOperations;
        final int numRetries = getNumberRetriesOnSameServer(request.getOverrideConfig());
        final URI uri = request.getUri();
        Server server = new Server(uri.getHost(), uri.getPort());
        final ServerStats serverStats = getServerStats(server);
        final Stopwatch tracer = getExecuteTracer().start();
        noteOpenConnection(serverStats, request);
        asyncClient.execute(request, new ResponseCallback<Response>() {
            private Response thisResponse;
            private Throwable thisException;
            @Override
            public void onResponseReceived(Response response) {
                thisResponse = response;
                onComplete();
                callback.onResponseReceived(response);
            }

            @Override
            public void onException(Throwable e) {
                thisException = e;
                onComplete();
                if (serverStats != null) {
                    serverStats.addToFailureCount();
                }
                if (isCircuitBreakerException(e) && serverStats != null) {
                    serverStats.incrementSuccessiveConnectionFailureCount();
                }
                boolean shouldRetry = retryOkayOnOperation && numRetries > 0 && isRetriableException(e);
                if (shouldRetry) {
                    if (retries.incrementAndGet() > numRetries) {
                        callback.onException(new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
                                "NUMBEROFRETRIESEXEEDED :" + numRetries + " retries, while making a RestClient call for: " + uri,
                                e !=null? e: new RuntimeException()));
                    } else {
                        logger.error("Exception while executing request which is deemed retry-able, retrying ..., SAME Server Retry Attempt #:" +
                                retries.get() +
                                ", URI:" +
                                uri);
                        try {
                            Thread.sleep((int) Math.pow(2.0, retries.get()) * 100); 
                        } catch (InterruptedException ex) {
                        }
                        tracer.start();
                        noteOpenConnection(serverStats, request);
                        try {
                            asyncClient.execute(request, this);
                        } catch (ClientException ex) {
                            callback.onException(ex);
                        }
                    } 
                } else {
                    ClientException clientException = generateNIWSException(uri.toString(), e);
                    callback.onException(clientException);
                }
            }
            
            private void onComplete() {
                tracer.stop();
                long duration = tracer.getDuration(TimeUnit.MILLISECONDS);
                noteRequestCompletion(serverStats, request, thisResponse, thisException, duration);
            }            
        });
    }

    
    @Override
    protected boolean isCircuitBreakerException(Throwable e) {
        return true;
    }

    @Override
    protected boolean isRetriableException(Throwable e) {
        return true;
    }
}
