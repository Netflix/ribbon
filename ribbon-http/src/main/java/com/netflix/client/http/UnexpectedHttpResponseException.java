package com.netflix.client.http;

public class UnexpectedHttpResponseException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    private final HttpResponse response;
    
    public UnexpectedHttpResponseException(HttpResponse response) {
        super(response.getStatusLine());
        this.response = response;
    }
    
    public int getStatusCode() {
        return response.getStatus();
    }
    
    public HttpResponse getResponse() {
        return response;
    }
}
