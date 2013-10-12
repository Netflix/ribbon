package com.netflix.ribbon.examples;

import java.util.Random;

import com.netflix.httpasyncclient.RibbonHttpAsyncClient;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.net.httpserver.HttpServer;

public abstract class ExampleAppWithLocalResource {

    int port = (new Random()).nextInt(1000) + 4000; 
    String SERVICE_URI = "http://localhost:" + port + "/";
    HttpServer server = null;
    RibbonHttpAsyncClient client = new RibbonHttpAsyncClient();
    
    public abstract void run() throws Exception;
    
    public final void runApp() throws Exception {
        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.ribbon.examples.server");
        try{
            server = HttpServerFactory.create(SERVICE_URI, resourceConfig);           
            server.start();
            run();
            // Thread.sleep(10000); // make sure server is running when run() is returned 
        } finally {
            client.close();
            System.err.println("Shut down server, this will take a while ...");
            if (server != null) {
                server.stop(0);
            }
        }
    }
}
