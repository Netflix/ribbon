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
    * @throws ClientException
    */
   public Object getPayload() throws ClientException;
      
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
