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
package com.netflix.niws.client.http;

import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;

@Produces({"application/xml"})
@Path("/test")
public class TestResource {
		
	@Path("/getObject")
	@GET
	public Response getObject(@QueryParam ("name") String name) {
		TestObject obj = new TestObject();
		obj.name = name;
		return Response.ok(obj).build();
	}

	@Path("/getJsonObject")
	@Produces("application/json")
	@GET
	public Response getJsonObject(@QueryParam ("name") String name) throws Exception {
	    TestObject obj = new TestObject();
	    obj.name = name;
	    ObjectMapper mapper = new ObjectMapper();
	    String value = mapper.writeValueAsString(obj);
	    return Response.ok(value).build();
	}


	@Path("/setObject")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	public Response setObject(TestObject obj) {
		return Response.ok(obj).build();
	}
	
	@Path("/setJsonObject")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
	public Response setJsonObject(String obj) throws Exception {
	    System.out.println("Get json string " + obj);
	    return Response.ok(obj).build();
	}

    @POST
    @Path("/postStream")
    @Consumes( { MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_XML})
    public Response handlePost(final InputStream in, @HeaderParam("Transfer-Encoding") String transferEncoding) {
        try {
            byte[] bytes = IOUtils.toByteArray(in);
            String entity = new String(bytes, "UTF-8");
            return Response.ok(entity).build();
        } catch (Exception e) {
            return Response.serverError().build();
        }
    }    
    
    @GET
    @Path("/get503")
    public Response get503() {
        return Response.status(503).build();
    }
    
    @GET
    @Path("/get500")
    public Response get500() {
        return Response.status(500).build();
    }

    @GET
    @Path("/getReadtimeout") 
    public Response getReadtimeout() {
        try {
            Thread.sleep(10000);
        } catch (Exception e) { // NOPMD
        }
        return Response.ok().build();
    }
    
    @POST
    @Path("/postReadtimeout") 
    public Response postReadtimeout() {
        try {
            Thread.sleep(10000);
        } catch (Exception e) { // NOPMD
        }
        return Response.ok().build();
    }


}
