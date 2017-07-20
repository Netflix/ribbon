package com.netflix.loadbalancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ServerTest {

    @Test
    public void createSchemeHost() {
        Server server = new Server("http://netflix.com");
        assertEquals("http", server.getScheme());
        assertEquals("netflix.com", server.getHost());
        assertEquals(80, server.getPort());
    }
    
    @Test
    public void createSchemeHostPort() {
        Server server = new Server("http://netflix.com:8080");
        assertEquals("http", server.getScheme());
        assertEquals("netflix.com", server.getHost());
        assertEquals(8080, server.getPort());
    }
    
    @Test
    public void createSecureSchemeHost() {
        Server server = new Server("https://netflix.com");
        assertEquals("https", server.getScheme());
        assertEquals("netflix.com", server.getHost());
        assertEquals(443, server.getPort());
    }
    
    @Test
    public void createSecureSchemeHostPort() {
        Server server = new Server("https://netflix.com:443");
        assertEquals("https", server.getScheme());
        assertEquals("netflix.com", server.getHost());
        assertEquals(443, server.getPort());
    }
    
    @Test
    public void createSecureSchemeHostPortExplicit() {
        Server server = new Server("https", "netflix.com", 443);
        assertEquals("https", server.getScheme());
        assertEquals("netflix.com", server.getHost());
        assertEquals(443, server.getPort());
    }
    
    @Test
    public void createHost() {
        Server server = new Server("netflix.com");
        assertNull(server.getScheme());
        assertEquals("netflix.com", server.getHost());
        assertEquals(80, server.getPort());
    }
    
    @Test
    public void createHostPort() {
        Server server = new Server("netflix.com:8080");
        assertNull(server.getScheme());
        assertEquals("netflix.com", server.getHost());
        assertEquals(8080, server.getPort());
    }
}
