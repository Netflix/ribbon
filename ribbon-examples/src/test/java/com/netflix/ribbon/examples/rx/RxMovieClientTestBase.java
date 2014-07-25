package com.netflix.ribbon.examples.rx;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServer;
import org.junit.After;
import org.junit.Before;

import java.util.Random;

/**
 * @author Tomasz Bak
 */
public class RxMovieClientTestBase {
    private static final Random RANDOM = new Random();
    protected int port = RANDOM.nextInt(1000) + 8000;

    private RxMovieServer movieServer;

    private HttpServer<ByteBuf, ByteBuf> httpServer;

    @Before
    public void setUp() throws Exception {
        movieServer = new RxMovieServer(port);
        httpServer = movieServer.createServer().start();
    }

    @After
    public void tearDown() throws Exception {
        httpServer.shutdown();
    }
}
