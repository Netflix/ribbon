package com.netflix.niws.client;

import java.net.URI;

import javax.ws.rs.core.MultivaluedMap;

/**
 * Response interface for the NIWS client framework.  Use this in your client proxy interface method return type
 * declarations if you want access to the response entity as well as status and header information.
 *
 * @author <a href="mailto:stonse@netflix.com">stonse</a>
 * @version $Revision: 1 $
 */
public interface IResponse
{
   
   /**
    * Returns the raw entity if available from the response 
    * @return
    * @throws IllegalArgumentException
    * @throws NIWSClientException
    */
   public Object getPayload() throws NIWSClientException;
      
   /**
    * A "peek" kinda API. Use to check if your service returned a response with an Entity
    * @return
    */
   public boolean hasPayload();
   
   /**
    * helper API - to stand for HTTP response code 200
    * @return
    */
   public boolean isSuccess();
   
      
   /**
    * Return the Request URI that generated this response
    * @return
    */
   public URI getRequestedURI();
   
   public MultivaluedMap<?, ?> getHeaders();   
}
