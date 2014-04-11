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
package com.netflix.ribbon.examples.server;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.codehaus.jackson.map.ObjectMapper;

import com.google.common.collect.Lists;
import com.thoughtworks.xstream.XStream;

@Path("/testAsync")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ServerResources {

    public static class Person {
        public String name;
        public int age;
        public Person() {}
        public Person(String name, int age) {
            super();
            this.name = name;
            this.age = age;
        }
        @Override
        public String toString() {
            return "Person [name=" + name + ", age=" + age + "]";
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + age;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Person other = (Person) obj;
            if (age != other.age)
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }
        
        
        
    }

    private static ObjectMapper mapper = new ObjectMapper();
    public static final Person defaultPerson = new Person("ribbon", 1);
    
    public static final List<Person> persons = Lists.newArrayList();
    
    public static final List<String> streamContent = Lists.newArrayList();
    
    static {
        for (int i = 0; i < 1000; i++) {
            streamContent.add("data: line " + i);
        }
        for (int i = 0; i < 100; i++) {
            persons.add(new Person(String.valueOf(i), 10));
        }
    }
    
    @GET
    @Path("/person")
    public Response getPerson() throws IOException {
        String content = mapper.writeValueAsString(defaultPerson);
        return Response.ok(content).build();
    }
    
    @GET
    @Path("/persons")
    public Response getPersons() throws IOException {
        String content = mapper.writeValueAsString(persons);
        return Response.ok(content).build();
    }

    
    @GET
    @Path("/noEntity")
    public Response getNoEntity() {
        return Response.ok().build();
    }
    
    @GET
    @Path("/readTimeout")
    public Response getReadTimeout() throws IOException, InterruptedException {
        Thread.sleep(10000);
        String content = mapper.writeValueAsString(defaultPerson);
        return Response.ok(content).build();
    }

    
    @POST
    @Path("/person")
    public Response createPerson(String content) throws IOException {
        System.err.println("uploaded: " + content);
        Person person = mapper.readValue(content, Person.class);
        return Response.ok(mapper.writeValueAsString(person)).build();
    }
    
    @GET
    @Path("/personQuery")
    public Response queryPerson(@QueryParam("name") String name, @QueryParam("age") int age) throws IOException {
        Person person = new Person(name, age);
        return Response.ok(mapper.writeValueAsString(person)).build();
    }
    
    @GET
    @Path("/stream")
    @Produces("text/event-stream")
    public StreamingOutput getStream() {
        return new StreamingOutput() {            
            @Override
            public void write(OutputStream output) throws IOException,
                    WebApplicationException {
                for (String line: streamContent) {
                    String eventLine = line + "\n\n";
                    output.write(eventLine.getBytes("UTF-8"));
                }
                output.close();
            }
        };
    }
    
    @GET
    @Path("/personStream")
    @Produces("text/event-stream")
    public StreamingOutput getPersonStream() {
        return new StreamingOutput() {            
            @Override
            public void write(OutputStream output) throws IOException,
                    WebApplicationException {
                for (Person p: persons) {
                    String eventLine = "data: " + mapper.writeValueAsString(p)
                            + "\n\n";
                    output.write(eventLine.getBytes("UTF-8"));
                }
                output.close();
            }
        };
    }

    
    @GET
    @Path("/customEvent")
    public StreamingOutput getCustomeEvents() {
        return new StreamingOutput() {            
            @Override
            public void write(OutputStream output) throws IOException,
                    WebApplicationException {
                for (String line: streamContent) {
                    String eventLine = line + "\n";
                    output.write(eventLine.getBytes("UTF-8"));
                }
                output.close();
            }
        };
    }

    
    @GET
    @Path("/getXml")
    @Produces("application/xml")
    public Response getXml() {
        XStream xstream = new XStream();
        String content = xstream.toXML(new Person("I am from XML", 1));
        return Response.ok(content).build();
    }
}

