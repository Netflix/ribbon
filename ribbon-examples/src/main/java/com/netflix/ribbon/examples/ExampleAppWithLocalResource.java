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
package com.netflix.ribbon.examples;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.net.httpserver.HttpServer;

/**
 * A base class for some sample applications that starts and stops a local server
 * 
 * @author awang
 *
 */
public abstract class ExampleAppWithLocalResource {

    public int port = (new Random()).nextInt(1000) + 4000; 
    public String SERVICE_URI = "http://localhost:" + port + "/";
    HttpServer server = null;
    
    public abstract void run() throws Exception;
    
    @edu.umd.cs.findbugs.annotations.SuppressWarnings
    public final void runApp() throws Exception {
        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.ribbon.examples.server");
        ExecutorService service = Executors.newFixedThreadPool(50);
        try{
            server = HttpServerFactory.create(SERVICE_URI, resourceConfig);           
            server.setExecutor(service);
            server.start();
            run();
        } finally {
            System.err.println("Shut down server ...");
            if (server != null) {
                server.stop(1);
            }
            service.shutdownNow();
        }
        System.exit(0);
    }
}
