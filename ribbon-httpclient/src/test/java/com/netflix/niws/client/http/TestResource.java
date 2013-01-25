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
	
	@Path("/setObject")
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	public Response setObject(TestObject obj) {
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
}
