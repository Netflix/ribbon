package com.netflix.ribbon.examples.rx;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServer;

import org.junit.After;
import org.junit.Before;

/**
 * @author Tomasz Bak
 */
public class RxMovieClientTestBase {
    protected int port = 0;

    private RxMovieServer movieServer;

    private HttpServer<ByteBuf, ByteBuf> httpServer;

    @Before
    public void setUp() throws Exception {
        movieServer = new RxMovieServer(port);
        httpServer = movieServer.createServer().start();
        port = httpServer.getServerPort();
    }

    @After
    public void tearDown() throws Exception {
        httpServer.shutdown();
    }
}
