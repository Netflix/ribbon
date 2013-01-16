package com.netflix.niws.client.http;

import java.io.InputStream;
import java.net.URI;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.niws.client.IResponse;
import com.netflix.niws.client.NIWSClientException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;

/**
 * A NIWS   Client Response
 * (this version just wraps Jersey Client response)
 * @author stonse
 *
 */
public class HttpClientResponse implements IResponse {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpClientResponse.class);
    
    private ClientResponse bcr = null;
        
    private URI requestedURI; // the request url that got this response
    
    public HttpClientResponse(ClientResponse cr){
        bcr = cr;
    }

     /**
        * Returns the raw entity if available from the response 
        * @return
        * @throws IllegalArgumentException
        * @throws NIWSClientException
        */
    public InputStream getRawEntity() throws NIWSClientException{
        return bcr.getEntityInputStream();
    }
       
    
    public <T> T getEntity(Class<T> c) throws IllegalArgumentException,
            NIWSClientException {
        T t = null;
        try {
            t = this.bcr.getEntity(c);
        } catch (UniformInterfaceException e) {
            throw new NIWSClientException(NIWSClientException.ErrorType.GENERAL, e.getMessage(), e.getCause());
        } 
        return t;
    }

    @Override
    public MultivaluedMap<String, String> getHeaders() {
        return bcr.getHeaders();
    }

    public int getStatus() {
        return bcr.getStatus();
    }

    @Override
    public boolean isSuccess() {
        boolean isSuccess = false;
        ClientResponse.Status s = bcr != null? bcr.getClientResponseStatus(): null;
        isSuccess = s!=null? (s.getFamily() == javax.ws.rs.core.Response.Status.Family.SUCCESSFUL): false;
        return isSuccess;
    }

    public boolean hasEntity() {
        return bcr.hasEntity();
    }
        
    @Override
    public URI getRequestedURI() {
       return this.requestedURI;
    }
    
    public void setRequestedURI(URI requestedURI) {
        this.requestedURI = requestedURI;
    }

    @Override
    public Object getPayload() throws NIWSClientException {
        if (hasEntity()) {
            return getRawEntity();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasPayload() {
        return hasEntity();        
    }
    
    public ClientResponse getJerseyClientResponse() {
        return bcr;
    }
    
    public void releaseResources() {
        try {
            bcr.close();
        } catch (Exception e) {
            logger.error("Error releasing connection", e);
        }
    }
}
