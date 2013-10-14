package com.netflix.ribbon.examples;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.net.httpserver.HttpServer;

public abstract class ExampleAppWithLocalResource {

    int port = (new Random()).nextInt(1000) + 4000; 
    String SERVICE_URI = "http://localhost:" + port + "/";
    HttpServer server = null;
    
    public abstract void run() throws Exception;
    
    public final void runApp() throws Exception {
        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.ribbon.examples.server");
        ExecutorService service = Executors.newFixedThreadPool(5);
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
    }
}
