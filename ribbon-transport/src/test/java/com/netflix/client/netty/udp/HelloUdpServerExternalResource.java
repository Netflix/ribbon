package com.netflix.client.netty.udp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramPacket;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.protocol.udp.server.UdpServer;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.functions.Func1;

public class HelloUdpServerExternalResource extends ExternalResource {
    private static final Logger LOG = LoggerFactory.getLogger(HelloUdpServerExternalResource.class);
    
    static final String WELCOME_MSG = "Welcome to the broadcast world!";
    static final byte[] WELCOME_MSG_BYTES = WELCOME_MSG.getBytes(Charset.defaultCharset());
    
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
        
        server = RxNetty.createUdpServer(port, new ConnectionHandler<DatagramPacket, DatagramPacket>() {
            @Override
            public Observable<Void> handle(final ObservableConnection<DatagramPacket, DatagramPacket> newConnection) {
                return newConnection.getInput().flatMap(new Func1<DatagramPacket, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(final DatagramPacket received) {
                        return Observable.interval(timeout, TimeUnit.MILLISECONDS).take(1).flatMap(new Func1<Long, Observable<Void>>() {
                            @Override
                            public Observable<Void> call(Long aLong) {
                                InetSocketAddress sender = received.sender();
                                LOG.info("Received datagram. Sender: " + sender);
                                ByteBuf data = newConnection.getChannel().alloc().buffer(WELCOME_MSG_BYTES.length);
                                data.writeBytes(WELCOME_MSG_BYTES);
                                return newConnection.writeAndFlush(new DatagramPacket(data, sender));
                            }
                        });
                    }
                });
            }
        });
        
        server.start();
        
        LOG.info("UDP hello server started at port: " + port);
    }

    /**
     * Override to tear down your specific external resource.
     */
    protected void after() {
        if (server != null) {
            try {
                server.shutdown();
            } catch (InterruptedException e) {
                LOG.error("Failed to shut down server", e);
            }
        }
    }
    
    public int getServerPort() {
        return server.getServerPort();
    }
}
