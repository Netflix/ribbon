package com.netflix.client.testutil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.sun.jersey.core.util.Base64;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

/**
 * Rule for running an embedded HTTP server for basic testing.  The
 * server uses ephemeral ports to ensure there are no conflicts when
 * running concurrent unit tests
 * 
 * The server uses HttpServer classes available in the JVM to avoid
 * pulling in any extra dependencies.
 * 
 * Available endpoints are,
 * 
 * /                      Returns a 200
 * /status?code=${code}   Returns a request provide code
 * /noresponse            No response from the server
 * 
 * Optional query parameters
 * delay=${delay}         Inject a delay into the request
 * 
 * @author elandau
 *
 */
public class MockHttpServer implements TestRule {
    public static final String TEST_TS1 =
            "/u3+7QAAAAIAAAABAAAAAgALcmliYm9uX3Jvb3QAAAFA9E6KkQAFWC41MDkAAAIyMIICLjCCAZeg" +
                    "AwIBAgIBATANBgkqhkiG9w0BAQUFADBTMRgwFgYDVQQDDA9SaWJib25UZXN0Um9vdDExCzAJBgNV" +
                    "BAsMAklUMRAwDgYDVQQKDAdOZXRmbGl4MQswCQYDVQQIDAJDQTELMAkGA1UEBhMCVVMwIBcNMTMw" +
                    "OTA2MTcyNTIyWhgPMjExMzA4MTMxNzI1MjJaMFMxGDAWBgNVBAMMD1JpYmJvblRlc3RSb290MTEL" +
                    "MAkGA1UECwwCSVQxEDAOBgNVBAoMB05ldGZsaXgxCzAJBgNVBAgMAkNBMQswCQYDVQQGEwJVUzCB" +
                    "nzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAg8riOgT2Y39SQlZE+MWnOiKjREZzQ3ecvPf40oF8" +
                    "9YPNGpBhJzIKdA0TR1vQ70p3Fl2+Y5txs1H2/iguOdFMBrSdv1H8qJG1UufaeYO++HBm3Mi2L02F" +
                    "6fcTEEyXQMebKCWf04mxvLy5M6B5yMqZ9rHEZD+qsF4rXspx70bd0tUCAwEAAaMQMA4wDAYDVR0T" +
                    "BAUwAwEB/zANBgkqhkiG9w0BAQUFAAOBgQBzTEn9AZniODYSRa+N7IvZu127rh+Sc6XWth68TBRj" +
                    "hThDFARnGxxe2d3EFXB4xH7qcvLl3HQ3U6lIycyLabdm06D3/jzu68mkMToE5sHJmrYNHHTVl0aj" +
                    "0gKFBQjLRJRlgJ3myUbbfrM+/a5g6S90TsVGTxXwFn5bDvdErsn8F8Hd41plMkW5ywsn6yFZMaFr" +
                    "MxnX";


    //  Keystore type: JKS
    //  Keystore provider: SUN
    //
    //  Your keystore contains 1 entry
    //
    //  Alias name: ribbon_root
    //  Creation date: Sep 6, 2013
    //  Entry type: trustedCertEntry
    //
    //  Owner: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestRoot2
    //  Issuer: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestRoot2
    //  Serial number: 1
    //  Valid from: Fri Sep 06 10:26:22 PDT 2013 until: Sun Aug 13 10:26:22 PDT 2113
    //  Certificate fingerprints:
    //       MD5:  44:64:E3:25:4F:D2:2C:8D:4D:B0:53:19:59:BD:B3:20
    //       SHA1: 26:2F:41:6D:03:C7:D0:8E:4F:AF:0E:4F:29:E3:08:53:B7:3C:DB:EE
    //       Signature algorithm name: SHA1withRSA
    //       Version: 3
    //
    //  Extensions:
    //
    //  #1: ObjectId: 2.5.29.19 Criticality=false
    //  BasicConstraints:[
    //    CA:true
    //    PathLen:2147483647
    //  ]

    public static final String TEST_TS2 =
            "/u3+7QAAAAIAAAABAAAAAgALcmliYm9uX3Jvb3QAAAFA9E92vgAFWC41MDkAAAIyMIICLjCCAZeg" +
                    "AwIBAgIBATANBgkqhkiG9w0BAQUFADBTMRgwFgYDVQQDDA9SaWJib25UZXN0Um9vdDIxCzAJBgNV" +
                    "BAsMAklUMRAwDgYDVQQKDAdOZXRmbGl4MQswCQYDVQQIDAJDQTELMAkGA1UEBhMCVVMwIBcNMTMw" +
                    "OTA2MTcyNjIyWhgPMjExMzA4MTMxNzI2MjJaMFMxGDAWBgNVBAMMD1JpYmJvblRlc3RSb290MjEL" +
                    "MAkGA1UECwwCSVQxEDAOBgNVBAoMB05ldGZsaXgxCzAJBgNVBAgMAkNBMQswCQYDVQQGEwJVUzCB" +
                    "nzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAnEwAfuHYKRJviVB3RyV3+/mp4qjWZZd/q+fE2Z0k" +
                    "o2N2rrC8fAw53KXwGOE5fED6wXd3B2zyoSFHVsWOeL+TUoohn+eHSfwH7xK+0oWC8IvUoXWehOft" +
                    "grYtv9Jt5qNY5SmspBmyxFiaiAWQJYuf12Ycu4Gqg+P7mieMHgu6Do0CAwEAAaMQMA4wDAYDVR0T" +
                    "BAUwAwEB/zANBgkqhkiG9w0BAQUFAAOBgQBNA0ask9eTYYhYA3bbmQZInxkBV74Gq/xorLlVygjn" +
                    "OgyGYp4/L274qwlPMqnQRmVbezkug2YlUK8xbrjwCUvHq2XW38e2RjK5q3EXVkGJxgCBuHug/eIf" +
                    "wD+/IEIE8aVkTW2j1QrrdkXDhRO5OsjvIVdy5/V4U0hVDnSo865ud9VQ/hZmOQuZItHViSoGSe2j" +
                    "bbZk";

    //  Keystore type: JKS
    //  Keystore provider: SUN
    //
    //  Your keystore contains 1 entry
    //
    //  Alias name: ribbon_key
    //  Creation date: Sep 6, 2013
    //  Entry type: PrivateKeyEntry
    //  Certificate chain length: 1
    //  Certificate[1]:
    //  Owner: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestEndEntity1
    //  Issuer: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestRoot1
    //  Serial number: 64
    //  Valid from: Fri Sep 06 10:25:22 PDT 2013 until: Sun Aug 13 10:25:22 PDT 2113
    //  Certificate fingerprints:
    //       MD5:  79:C5:F3:B2:B8:4C:F2:3F:2E:C7:67:FC:E7:04:BF:90
    //       SHA1: B1:D0:4E:A6:D8:84:BF:8B:01:46:B6:EA:97:5B:A0:4E:13:8B:A9:DE
    //       Signature algorithm name: SHA1withRSA
    //       Version: 3

    public static final String TEST_KS1 =
            "/u3+7QAAAAIAAAABAAAAAQAKcmliYm9uX2tleQAAAUD0Toq4AAACuzCCArcwDgYKKwYBBAEqAhEB" +
                    "AQUABIICo9fLN8zeXLcyx50+6B6gdXiUslZKY0SgLwL8kKNlQFiccD3Oc1yWjMnVC2QOdLsFzcVk" +
                    "ROhgMH/nHfFeXFlvY5IYMXqhbEC37LjE52RtX5KHv4FLYxxZCHduAwO8UTPa603XzrJ0VTMJ6Hso" +
                    "9+Ql76cGxPtIPcYm8IfqIY22as3NlKO4eMbiur9GLvuC57eql8vROaxGy8y657gc6kZMUyQOC+HG" +
                    "a5M3DTFpjl4V6HHbXHhMNEk9eXHnrZwYVOJmOgdgIrNNHOyD4kE+k21C7rUHhLAwK84wKL/tW4k9" +
                    "xnhOJK/L1RmycRIFWwXVi3u/3vi49bzdZsRLn73MdQkTe5p8oNZzG9sxg76u67ua6+99TMZYE1ay" +
                    "5JCYgbr85KbRsoX9Hd5XBcSNzROKJl0To2tAF8eTTMRlhEy7JZyTF2M9877juNaregVwE3Tp+a/J" +
                    "ACeNMyrxOQItNDam7a5dgBohpM8oJdEFqqj/S9BU7H5sR0XYo8TyIe1BV9zR5ZC/23fj5l5zkrri" +
                    "TCMgMbvt95JUGOT0gSzxBMmhV+ZLxpmVz3M5P2pXX0DXGTKfuHSiBWrh1GAQL4BOVpuKtyXlH1/9" +
                    "55/xY25W0fpLzMiQJV7jf6W69LU0FAFWFH9uuwf/sFph0S1QQXcQSfpYmWPMi1gx/IgIbvT1xSuI" +
                    "6vajgFqv6ctiVbFAJ6zmcnGd6e33+Ao9pmjs5JPZP3rtAYd6+PxtlwUbGLZuqIVK4o68LEISDfvm" +
                    "nGlk4/1+S5CILKVqTC6Ja8ojwUjjsNSJbZwHue3pOkmJQUNtuK6kDOYXgiMRLURbrYLyen0azWw8" +
                    "C5/nPs5J4pN+irD/hhD6cupCnUJmzMw30u8+LOCN6GaM5fdCTQ2uQKF7quYuD+gR3lLNOqq7KAAA" +
                    "AAEABVguNTA5AAACJTCCAiEwggGKoAMCAQICAWQwDQYJKoZIhvcNAQEFBQAwUzEYMBYGA1UEAwwP" +
                    "UmliYm9uVGVzdFJvb3QxMQswCQYDVQQLDAJJVDEQMA4GA1UECgwHTmV0ZmxpeDELMAkGA1UECAwC" +
                    "Q0ExCzAJBgNVBAYTAlVTMCAXDTEzMDkwNjE3MjUyMloYDzIxMTMwODEzMTcyNTIyWjBYMR0wGwYD" +
                    "VQQDDBRSaWJib25UZXN0RW5kRW50aXR5MTELMAkGA1UECwwCSVQxEDAOBgNVBAoMB05ldGZsaXgx" +
                    "CzAJBgNVBAgMAkNBMQswCQYDVQQGEwJVUzCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAydqk" +
                    "AJuUcSF8dpUbNJWl+G1usgHtEFEbOMm54N/ZqGC7iSYs6EXfeoEyiHrMw/hdCKACERq2vuiuqan8" +
                    "h6z65/DXIiHUyykGb/Z4NK1I0aCQLZG4Ek3sERilILWyy2NRpjUrvqDPr/mQgymXqpuYhSD81jHx" +
                    "F84AOpTrnGsY7/sCAwEAATANBgkqhkiG9w0BAQUFAAOBgQAjvtRKNhb1R6XIuWaOxJ0XDLine464" +
                    "Ie7LDfkE/KB43oE4MswjRh7nR9q6C73oa6TlIXmW6ysyKPp0vAyWHlq/zZhL3gNQ6faHuYHqas5s" +
                    "nJQgvQpHAQh4VXRyZt1K8ZdsHg3Qbd4APTL0aRVQkxDt+Dxd6AsoRMKmO/c5CRwUFIV/CK7k5VSh" +
                    "Sl5PRtH3PVj2vp84";

    //  Keystore type: JKS
    //  Keystore provider: SUN
    //
    //  Your keystore contains 1 entry
    //
    //  Alias name: ribbon_key
    //  Creation date: Sep 6, 2013
    //  Entry type: PrivateKeyEntry
    //  Certificate chain length: 1
    //  Certificate[1]:
    //  Owner: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestEndEntity2
    //  Issuer: C=US, ST=CA, O=Netflix, OU=IT, CN=RibbonTestRoot2
    //  Serial number: 64
    //  Valid from: Fri Sep 06 10:26:22 PDT 2013 until: Sun Aug 13 10:26:22 PDT 2113
    //  Certificate fingerprints:
    //       MD5:  C3:AF:6A:DB:18:ED:70:22:83:73:9A:A5:DB:58:6D:04
    //       SHA1: B7:D4:F0:87:A8:4E:49:0A:91:B1:7B:62:28:CA:A2:4A:0E:AE:40:CC
    //       Signature algorithm name: SHA1withRSA
    //       Version: 3
    //
    //
    //

    public static final String TEST_KS2 =
            "/u3+7QAAAAIAAAABAAAAAQAKcmliYm9uX2tleQAAAUD0T3bNAAACuzCCArcwDgYKKwYBBAEqAhEB" +
                    "AQUABIICoysDP4inwmxxKZkS8EYMW3DCJCD1AmpwFHxJIzo2v9fMysg+vjKxsrvVyKG23ZcHcznI" +
                    "ftmrEpriCCUZp+NNAf0EJWVAIzGenwrsd0+rI5I96gBOh9slJUzgucn7164R3XKNKk+VWcwGJRh+" +
                    "IuHxVrwFN025pfhlBJXNGJg4ZlzB7ZwcQPYblBzhLbhS3vJ1Vc46pEYWpnjxmHaDSetaQIcueAp8" +
                    "HnTUkFMXJ6t51N0u9QMPhBH7p7N8tNjaa5noSdxhSl/2Znj6r04NwQU1qX2n4rSWEnYaW1qOVkgx" +
                    "YrQzxI2kSHZfQDM2T918UikboQvAS1aX4h5P3gVCDKLr3EOO6UYO0ZgLHUr/DZrhVKd1KAhnzaJ8" +
                    "BABxot2ES7Zu5EzY9goiaYDA2/bkURmt0zDdKpeORb7r59XBZUm/8D80naaNnE45W/gBA9bCiDu3" +
                    "R99xie447c7ZX9Jio25yil3ncv+npBO1ozc5QIgQnbEfxbbwii3//shvPT6oxYPrcwWBXnaJNC5w" +
                    "2HDpCTXJZNucyjnNVVxC7p1ANNnvvZhgC0+GpEqmf/BW+fb9Qu+AXe0/h4Vnoe/Zs92vPDehpaKy" +
                    "oe+jBlUNiW2bpR88DSqxVcIu1DemlgzPa1Unzod0FdrOr/272bJnB2zAo4OBaBSv3KNf/rsMKjsU" +
                    "X2Po77+S+PKoQkqd8KJFpmLEb0vxig9JsrTDJXLf4ebeSA1W7+mBotimMrp646PA3NciMSbS4csh" +
                    "A7o/dBYhHlEosVgThm1JknIKhemf+FZsOzR3bJDT1oXJ/GhYpfzlCLyVFBeVP0KRnhih4xO0MEO7" +
                    "Q21DBaTTqAvUo7Iv3/F3mGMOanLcLgoRoq3moQ7FhfCDRtRAPA1qT2+pxPG5wqlGeYc6McOvogAA" +
                    "AAEABVguNTA5AAACJTCCAiEwggGKoAMCAQICAWQwDQYJKoZIhvcNAQEFBQAwUzEYMBYGA1UEAwwP" +
                    "UmliYm9uVGVzdFJvb3QyMQswCQYDVQQLDAJJVDEQMA4GA1UECgwHTmV0ZmxpeDELMAkGA1UECAwC" +
                    "Q0ExCzAJBgNVBAYTAlVTMCAXDTEzMDkwNjE3MjYyMloYDzIxMTMwODEzMTcyNjIyWjBYMR0wGwYD" +
                    "VQQDDBRSaWJib25UZXN0RW5kRW50aXR5MjELMAkGA1UECwwCSVQxEDAOBgNVBAoMB05ldGZsaXgx" +
                    "CzAJBgNVBAgMAkNBMQswCQYDVQQGEwJVUzCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAtOvK" +
                    "fBMC/oMo4xf4eYGN7hTNn+IywaSaVYndqECIfuznoIqRKSbeCKPSGs4CN1D+u3E2UoXcsDmTguHU" +
                    "fokDA7sLUu8wD5ndAXfCkP3gXlFtUpNz/jPaXDsMFntTn2BdLKccxRxNwtwC0zzwIdtx9pw/Ru0g" +
                    "NXIQnPi50aql5WcCAwEAATANBgkqhkiG9w0BAQUFAAOBgQCYWM2ZdBjG3jCvMw/RkebMLkEDRxVM" +
                    "XU63Ygo+iCZUk8V8d0/S48j8Nk/hhVGHljsHqE/dByEF77X6uHaDGtt3Xwe+AYPofoJukh89jKnT" +
                    "jEDtLF+y5AVfz6b2z3TnJcuigMr4ZtBFv18R00KLnVAznl/waXG8ix44IL5ss6nRZBJE4jr+ZMG9" +
                    "9I4P1YhySxo3Qd3g";

    public static final String PASSWORD = "changeit";
    
    public static final String INTERNALERROR_PATH = "/internalerror";
    public static final String NORESPONSE_PATH = "/noresponse";
    public static final String STATUS_PATH = "/status";
    public static final String OK_PATH = "/ok";
    public static final String ROOT_PATH = "/";
    
    private static final int DEFAULT_THREAD_COUNT = 10;
    private static final String DELAY_QUERY_PARAM = "delay";
    
    private HttpServer server;
    private int localHttpServerPort = 0;
    private ExecutorService service;
    private int threadCount = DEFAULT_THREAD_COUNT;
    private LinkedHashMap<String, HttpHandler> handlers = new LinkedHashMap<String, HttpHandler>();
    private boolean hasSsl = false;
    private File keystore;
    private File truststore;

    private String GENERIC_RESPONSE = "GenericTestHttpServer Response";
    
    public MockHttpServer() {
        handlers.put(ROOT_PATH, new TestHttpHandler() {
            @Override
            protected void handle(RequestContext context) throws IOException {
                context.response(200, GENERIC_RESPONSE);
            }});
        
        handlers.put(OK_PATH, new TestHttpHandler() {
            @Override
            protected void handle(RequestContext context) throws IOException {
                context.response(200, GENERIC_RESPONSE);
            }});
        
        handlers.put(STATUS_PATH, new TestHttpHandler() {
            @Override
            protected void handle(RequestContext context) throws IOException {
                context.response(Integer.parseInt(context.query("code")), GENERIC_RESPONSE);
            }});
        
        handlers.put(NORESPONSE_PATH, new TestHttpHandler() {
            @Override
            protected void handle(RequestContext context) throws IOException {
            }});
        
        handlers.put(INTERNALERROR_PATH, new TestHttpHandler() {
            @Override
            protected void handle(RequestContext context) throws IOException {
                throw new RuntimeException("InternalError");
            }});
    }
    
    public MockHttpServer handler(String path, HttpHandler handler) {
        handlers.put(path, handler);
        return this;
    }
    
    public MockHttpServer port(int port) {
        this.localHttpServerPort = port;
        return this;
    }
    
    public MockHttpServer secure() {
        this.hasSsl = true;
        return this;
    }
    
    public MockHttpServer threadCount(int threads) {
        this.threadCount = threads;
        return this;
    }
    
    @Override
    public Statement apply(final Statement statement, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                before(description);
                try {
                    statement.evaluate();
                } finally {
                    after(description);
                }
            }
        };
    }

    private static interface RequestContext {
        void response(int code, String body) throws IOException;
        String query(String key);
    }
    
    private static abstract class TestHttpHandler implements HttpHandler {
        @Override
        public final void handle(final HttpExchange t) throws IOException {
            try {
                final Map<String, String> queryParameters = queryToMap(t);
                
                if (queryParameters.containsKey(DELAY_QUERY_PARAM)) {
                    Long delay = Long.parseLong(queryParameters.get(DELAY_QUERY_PARAM));
                    if (delay != null) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                
                handle(new RequestContext() {
                    @Override
                    public void response(int code, String body) throws IOException {
                        OutputStream os = t.getResponseBody();
                        t.sendResponseHeaders(code, body.length());
                        os.write(body.getBytes());
                        os.close();
                    }
    
                    @Override
                    public String query(String key) {
                        return queryParameters.get(key);
                    } 
                });
            }
            catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                String body = sw.toString();
                
                OutputStream os = t.getResponseBody();
                t.sendResponseHeaders(500, body.length());
                os.write(body.getBytes());
                os.close();
            }
        }
        
        protected abstract void handle(RequestContext context) throws IOException;
        
        private static Map<String, String> queryToMap(HttpExchange t) {
            String queryString = t.getRequestURI().getQuery();
            Map<String, String> result = new HashMap<String, String>();
            if (queryString != null) {
                for (String param : queryString.split("&")) {
                    String pair[] = param.split("=");
                    if (pair.length>1) {
                        result.put(pair[0], pair[1]);
                    }
                    else{
                        result.put(pair[0], "");
                    }
                }
            }
            return result;
        }

    }
    
    public void before(final Description description) throws Exception {
        this.service = Executors.newFixedThreadPool(
                threadCount, 
                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("TestHttpServer-%d").build());
        
        InetSocketAddress inetSocketAddress = new InetSocketAddress("localhost", 0);
        if (hasSsl) {
            byte[] sampleTruststore1 = Base64.decode(TEST_TS1);
            byte[] sampleKeystore1 = Base64.decode(TEST_KS1);

            keystore = File.createTempFile("SecureAcceptAllGetTest", ".keystore");
            truststore = File.createTempFile("SecureAcceptAllGetTest", ".truststore");

            FileOutputStream keystoreFileOut = new FileOutputStream(keystore);
            try {
                keystoreFileOut.write(sampleKeystore1);
            } finally {
                keystoreFileOut.close();
            }

            FileOutputStream truststoreFileOut = new FileOutputStream(truststore);
            try {
                truststoreFileOut.write(sampleTruststore1);
            } finally {
                truststoreFileOut.close();
            }


            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(keystore), PASSWORD.toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, PASSWORD.toCharArray());

            KeyStore ts = KeyStore.getInstance("JKS");
            ts.load(new FileInputStream(truststore), PASSWORD.toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            
            HttpsServer secureServer = HttpsServer.create(inetSocketAddress, 0);
            secureServer.setHttpsConfigurator(new HttpsConfigurator(sc) {
                public void configure (HttpsParameters params) {
                    SSLContext c = getSSLContext();
                    SSLParameters sslparams = c.getDefaultSSLParameters();
                    params.setSSLParameters(sslparams);
                }
            });
            server = secureServer;
        }
        else {
            server = HttpServer.create(inetSocketAddress, 0);
        }
        
        server.setExecutor(service);
        
        for (Entry<String, HttpHandler> handler : handlers.entrySet()) {
            server.createContext(handler.getKey(), handler.getValue());
        }
        
        server.start();
        localHttpServerPort = server.getAddress().getPort();
        
        System.out.println(description.getClassName() + " TestServer is started: " + getServerUrl());
    }

    public void after(final Description description) {
        try{
            server.stop(0);
            ((ExecutorService) server.getExecutor()).shutdownNow();
            
            System.out.println(description.getClassName() + " TestServer is shutdown: " + getServerUrl());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @return Get the root server URL
     */
    public String getServerUrl() {
        if (hasSsl) {
            return "https://localhost:" + localHttpServerPort;
        }
        else {
            return "http://localhost:" + localHttpServerPort;
        }
    }

    /**
     * @return Get the root server URL
     * @throws URISyntaxException 
     */
    public URI getServerURI() throws URISyntaxException {
        return new URI(getServerUrl());
    }

    /**
     * @param path
     * @return  Get a path to this server
     */
    public String getServerPath(String path) {
        return getServerUrl() + path;
    }

    /**
     * @param path
     * @return  Get a path to this server
     */
    public URI getServerPathURI(String path) throws URISyntaxException {
        return new URI(getServerUrl() + path);
    }

    /**
     * @return Return the ephemeral port used by this server
     */
    public int getServerPort() {
        return localHttpServerPort;
    }
    
    public File getKeyStore() {
        return keystore;
    }
    
    public File getTrustStore() {
        return truststore;
    }
}