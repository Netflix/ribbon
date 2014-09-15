package com.netflix.client.netty.udp;

import io.netty.channel.socket.DatagramPacket;
import io.reactivex.netty.protocol.udp.server.UdpServer;

import java.net.DatagramSocket;
import java.net.SocketException;

import org.junit.rules.ExternalResource;

public class HelloUdpServerExternalResource extends ExternalResource {
    private UdpServer<DatagramPacket, DatagramPacket> server;
    
    private int timeout = 0;
    
    public HelloUdpServerExternalResource() {
    }
    
    private int choosePort() throws SocketException {
        DatagramSocket serverSocket = new DatagramSocket();
        int port = serverSocket.getLocalPort();
        serverSocket.close();
        return port;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void start() {
        int port;
        try {
            port = choosePort();
        } catch (SocketException e) {
            throw new RuntimeException("Error choosing point", e);
        }
        server = new HelloUdpServer(port, timeout).createServer();
        server.start();
    }

    /**
     * Override to tear down your specific external resource.
     */
    protected void after() {
        if (server != null) {
            try {
                server.shutdown();
            } catch (InterruptedException e) {
                System.out.println("Failed to shut down server");
                e.printStackTrace();
            }
        }
    }
    
    public int getServerPort() {
        return server.getServerPort();
    }
}
