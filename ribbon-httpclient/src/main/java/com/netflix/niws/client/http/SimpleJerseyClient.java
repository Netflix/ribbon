package com.netflix.niws.client.http;

import java.io.File;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.util.HttpVerbUriRegexPropertyValue;
import com.netflix.http4.NFHttpClient;
import com.netflix.http4.NFHttpClientConstants;
import com.netflix.http4.NFHttpClientFactory;
import com.netflix.http4.NFHttpMethodRetryHandler;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.client.AbstractLoadBalancerAwareClient;
import com.netflix.niws.client.NIWSClientException;
import com.netflix.niws.client.NiwsClientConfig;
import com.netflix.niws.client.URLSslContextFactory;
import com.netflix.niws.client.NiwsClientConfig.NiwsClientConfigKey;
import com.netflix.niws.client.http.HttpClientRequest.Verb;
import com.netflix.util.Pair;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.ApacheHttpClient4Handler;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;

public class SimpleJerseyClient extends AbstractLoadBalancerAwareClient<HttpClientRequest, HttpClientResponse> {

    private Client restClient;
    private HttpClient httpClient4;
    private NiwsClientConfig ncc;
    private String restClientName;

    private boolean enableConnectionPoolCleanerTask = false;
    private DynamicIntProperty connIdleEvictTimeMilliSeconds;
    private int connectionCleanerRepeatInterval;
    private int maxConnectionsperHost;
    private int maxTotalConnections;
    private int connectionTimeout;
    private int readTimeout;
    private SpecializedDynamicIntProperty readTimeoutFastProperty;
    private String proxyHost;
    private int proxyPort;
    private boolean isSecure;
    private ApacheHttpClient4Config config;

    boolean bFollowRedirects = NiwsClientConfig.DEFAULT_FOLLOW_REDIRECTS;

    private static final Logger logger = LoggerFactory.getLogger(SimpleJerseyClient.class);

    class SpecializedDynamicIntProperty {

        DynamicProperty instanceSpecificProperty;
        DynamicIntProperty defaultProperty;

        public SpecializedDynamicIntProperty(NiwsClientConfigKey configKey) {
            instanceSpecificProperty = DynamicProperty.getInstance((NiwsClientConfig.getInstancePropName(restClientName, configKey)));
            defaultProperty = DynamicPropertyFactory.getInstance().getIntProperty(NiwsClientConfig.getDefaultPropName(configKey),
                    Integer.parseInt(ncc.getProperty(configKey).toString()));
        }

        int get() {
            return instanceSpecificProperty.getInteger(defaultProperty.get()).intValue();
        }

        // For JMX Monitored Resource
        @Override
        public String toString() {
            return Integer.toString(get());
        }
    }

    public SimpleJerseyClient() {
        restClientName = "default";
    }

    public SimpleJerseyClient(NiwsClientConfig ncc) {
        initWithNiwsConfig(ncc);
    }

    public SimpleJerseyClient(Client jerseyClient) {
        this.restClient = jerseyClient;
    }
    
    @Override
    public void initWithNiwsConfig(NiwsClientConfig clientConfig) {
        super.initWithNiwsConfig(clientConfig);
        this.ncc = clientConfig;
        restClientName = ncc.getClientName();
        if (ncc.getProperty(NiwsClientConfigKey.FollowRedirects)!=null){
            Boolean followRedirects = Boolean.valueOf(""+ncc.getProperty(NiwsClientConfigKey.FollowRedirects, "true"));
            bFollowRedirects = followRedirects.booleanValue();
        }
        config = new DefaultApacheHttpClient4Config();
        config.getProperties().put(
                ApacheHttpClient4Config.PROPERTY_CONNECT_TIMEOUT,
                Integer.parseInt(String.valueOf(ncc.getProperty(NiwsClientConfigKey.ConnectTimeout))));
        config.getProperties().put(
                ApacheHttpClient4Config.PROPERTY_READ_TIMEOUT,
                Integer.parseInt(String.valueOf(ncc.getProperty(NiwsClientConfigKey.ReadTimeout))));

        restClient = apacheHttpClientSpecificInitialization();
    }

    protected Client apacheHttpClientSpecificInitialization() {
        httpClient4 = NFHttpClientFactory.getNamedNFHttpClient(restClientName);

        if (httpClient4 instanceof AbstractHttpClient) {
            // DONT use our NFHttpClient's default Retry Handler since we have
            // retry handling (same server/next server) in RestClient itself
            ((AbstractHttpClient) httpClient4).setHttpRequestRetryHandler(new NFHttpMethodRetryHandler(restClientName, 0, false, 0));
        } else {
            logger.warn("Unexpected error: Unable to disable NFHttpClient "
                    + "retry handler, this most likely will not cause an "
                    + "issue but probably should be looked at");
        }

        HttpParams httpClientParams = httpClient4.getParams();
        // initialize Connection Manager cleanup facility
        NFHttpClient nfHttpClient = (NFHttpClient) httpClient4;
        // should we enable connection cleanup for idle connections?
        try {
            enableConnectionPoolCleanerTask = new Boolean(String.valueOf(ncc.getProperty(NiwsClientConfigKey.ConnectionPoolCleanerTaskEnabled,
                    NFHttpClientConstants.DEFAULT_CONNECTIONIDLE_TIMETASK_ENABLED))).booleanValue();
            nfHttpClient.getConnPoolCleaner().setEnableConnectionPoolCleanerTask(enableConnectionPoolCleanerTask);
        } catch (Exception e1) {
            throw new IllegalArgumentException("Invalid value for property:"
                    + NiwsClientConfigKey.ConnectionPoolCleanerTaskEnabled, e1);
        }
        if (enableConnectionPoolCleanerTask) {
            try {
                connectionCleanerRepeatInterval = Integer
                .parseInt(String.valueOf(ncc.getProperty(NiwsClientConfigKey.ConnectionCleanerRepeatInterval,
                        NFHttpClientConstants.DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS)));
                nfHttpClient.getConnPoolCleaner().setConnectionCleanerRepeatInterval(connectionCleanerRepeatInterval);
            } catch (Exception e1) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + NiwsClientConfigKey.ConnectionCleanerRepeatInterval, e1);
            }

            try {
                int iConnIdleEvictTimeMilliSeconds = Integer
                .parseInt(""
                        + ncc
                        .getProperty(NiwsClientConfigKey.ConnIdleEvictTimeMilliSeconds,
                                NFHttpClientConstants.DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS));
                connIdleEvictTimeMilliSeconds = DynamicPropertyFactory.getInstance().getIntProperty(
                        restClientName
                        + ".nfhttpclient.connIdleEvictTimeMilliSeconds",
                        iConnIdleEvictTimeMilliSeconds);
                nfHttpClient.setConnIdleEvictTimeMilliSeconds(connIdleEvictTimeMilliSeconds);
            } catch (Exception e1) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + NiwsClientConfigKey.ConnIdleEvictTimeMilliSeconds,
                        e1);
            }

            nfHttpClient.initConnectionCleanerTask();
        }

        try {
            maxConnectionsperHost = Integer
            .parseInt(""
                    + ncc
                    .getProperty(NiwsClientConfigKey.MaxHttpConnectionsPerHost,
                            maxConnectionsperHost));
            ClientConnectionManager connMgr = httpClient4.getConnectionManager();
            if (connMgr instanceof ThreadSafeClientConnManager) {
                ((ThreadSafeClientConnManager) connMgr)
                .setDefaultMaxPerRoute(maxConnectionsperHost);
            }
        } catch (Exception e1) {
            throw new IllegalArgumentException("Invalid value for property:"
                    + NiwsClientConfigKey.MaxHttpConnectionsPerHost, e1);
        }

        try {
            maxTotalConnections = Integer
            .parseInt(""
                    + ncc
                    .getProperty(NiwsClientConfigKey.MaxTotalHttpConnections,
                            maxTotalConnections));
            ClientConnectionManager connMgr = httpClient4
            .getConnectionManager();
            if (connMgr instanceof ThreadSafeClientConnManager) {
                ((ThreadSafeClientConnManager) connMgr)
                .setMaxTotal(maxTotalConnections);
            }
        } catch (Exception e1) {
            throw new IllegalArgumentException("Invalid value for property:"
                    + NiwsClientConfigKey.MaxTotalHttpConnections, e1);
        }

        try {
            connectionTimeout = Integer.parseInt(""
                    + ncc.getProperty(NiwsClientConfigKey.ConnectTimeout,
                            connectionTimeout));
            HttpConnectionParams.setConnectionTimeout(httpClientParams,
                    connectionTimeout);
        } catch (Exception e1) {
            throw new IllegalArgumentException("Invalid value for property:"
                    + NiwsClientConfigKey.ConnectTimeout, e1);
        }

        try {
            readTimeout = Integer.parseInt(""
                    + ncc.getProperty(NiwsClientConfigKey.ReadTimeout,
                            readTimeout));
            readTimeoutFastProperty = new SpecializedDynamicIntProperty(
                    NiwsClientConfigKey.ReadTimeout);
            HttpConnectionParams.setSoTimeout(httpClientParams, readTimeout);
        } catch (Exception e1) {
            throw new IllegalArgumentException("Invalid value for property:"
                    + NiwsClientConfigKey.ReadTimeout, e1);
        }

        // httpclient 4 seems to only have one buffer size controlling both
        // send/receive - so let's take the bigger of the two values and use
        // it as buffer size
        int bufferSize = Integer.MIN_VALUE;
        if (ncc.getProperty(NiwsClientConfigKey.ReceiveBuffferSize) != null) {
            try {
                bufferSize = Integer
                .parseInt(""
                        + ncc
                        .getProperty(NiwsClientConfigKey.ReceiveBuffferSize));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + NiwsClientConfigKey.ReceiveBuffferSize,
                        e);
            }
            if (ncc.getProperty(NiwsClientConfigKey.SendBufferSize) != null) {
                try {
                    int sendBufferSize = Integer
                    .parseInt(""
                            + ncc
                            .getProperty(NiwsClientConfigKey.SendBufferSize));
                    if (sendBufferSize > bufferSize) {
                        bufferSize = sendBufferSize;
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Invalid value for property:"
                            + NiwsClientConfigKey.SendBufferSize,
                            e);
                }
            }
        }
        if (bufferSize != Integer.MIN_VALUE) {
            HttpConnectionParams.setSocketBufferSize(httpClientParams,
                    bufferSize);
        }

        if (ncc.getProperty(NiwsClientConfigKey.StaleCheckingEnabled) != null) {
            try {
                HttpConnectionParams
                .setStaleCheckingEnabled(httpClientParams,
                        new Boolean(
                                ""
                                + ncc
                                .getProperty(NiwsClientConfigKey.StaleCheckingEnabled))
                .booleanValue());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + NiwsClientConfigKey.StaleCheckingEnabled,
                        e);
            }
        }

        if (ncc.getProperty(NiwsClientConfigKey.Linger) != null) {
            try {
                HttpConnectionParams.setLinger(httpClientParams,
                        Integer.parseInt(""
                                + ncc.getProperty(NiwsClientConfigKey.Linger)));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + NiwsClientConfigKey.Linger,
                        e);
            }
        }

        if (ncc.getProperty(NiwsClientConfigKey.ProxyHost) != null) {
            try {
                proxyHost = (String) ncc
                .getProperty(NiwsClientConfigKey.ProxyHost);
                proxyPort = Integer.parseInt(""
                        + ncc.getProperty(NiwsClientConfigKey.ProxyPort));
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                httpClient4
                .getParams()
                .setParameter(ConnRouteParams.DEFAULT_PROXY, proxy);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + NiwsClientConfigKey.ProxyHost,
                        e);
            }
        }

        if (isSecure) {
            final URL trustStoreUrl = getResourceForOptionalProperty(NiwsClientConfigKey.TrustStore);
            final URL keyStoreUrl = getResourceForOptionalProperty(NiwsClientConfigKey.KeyStore);

            if (trustStoreUrl != null || keyStoreUrl != null) {
                try {
                    final ClientConnectionManager currentManager = httpClient4
                    .getConnectionManager();

                    SSLContext context = new URLSslContextFactory(
                            trustStoreUrl,
                            (String) ncc
                            .getProperty(NiwsClientConfigKey.TrustStorePassword),
                            keyStoreUrl,
                            (String) ncc
                            .getProperty(NiwsClientConfigKey.KeyStorePassword))
                    .getSSLContext();
                    currentManager.getSchemeRegistry().register(new Scheme(
                            "https",
                            443,
                            new SSLSocketFactory(
                                    context,
                                    (X509HostnameVerifier) null)));
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Unable to configure custom secure socket factory",
                            e);
                }
            }
        }
        ApacheHttpClient4Handler handler = new ApacheHttpClient4Handler(httpClient4, new BasicCookieStore(), false);
        return new ApacheHttpClient4(handler, config);
    }

    public static URL getResource(String resourceName)
    {
        URL url = null;
        // attempt to load from the context classpath
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            url = loader.getResource(resourceName);
        }
        if (url == null) {
            // attempt to load from the system classpath
            url = ClassLoader.getSystemResource(resourceName);
        }
        if (url == null) {
            // attempt to load from the system classpath
            url = ConfigurationManager.class.getResource(resourceName);
        }
        if (url == null) {
            // attempt to load from the system classpath
            url = ConfigurationManager.class.getClassLoader().getResource(resourceName);
        }
        if (url == null) {
            try {
                resourceName = URLDecoder.decode(resourceName, "UTF-8");
                url = (new File(resourceName)).toURI().toURL();
            } catch (Exception e) {
                
            }
        }
        return url;
    }

    public Client getJerseyClient() {
        return restClient;
    }

    public void setJerseyClient(Client c) {
        restClient = c;
    }

    private URL getResourceForOptionalProperty(final NiwsClientConfigKey configKey) {
        final String propValue = (String) ncc.getProperty(configKey);
        URL result = null;

        if (propValue != null) {
            result = getResource(propValue);
            if (result == null) {
                throw new IllegalArgumentException("No resource found for " + configKey + ": "
                        + propValue);
            }
        }
        return result;
    }
    
    @Override
    public HttpClientResponse execute(HttpClientRequest task) throws Exception {
        return execute(task.getVerb(), task.getUri(),
                task.getHeaders(), task.getQueryParams(), task.getOverrideConfig(), task.getEntity());
    }

    private boolean isSecure(NiwsClientConfig overriddenClientConfig) {
        boolean isSecure = false;
        if (overriddenClientConfig != null && overriddenClientConfig.containsProperty(NiwsClientConfigKey.IsSecure)){
            isSecure = overriddenClientConfig.isSecure();
        } else {// look at the default client config
            if (ncc != null && ncc.containsProperty(NiwsClientConfigKey.IsSecure)){
                isSecure = ncc.isSecure();
            }
        }
        return isSecure;
    }

    @Override
    protected Pair<String, Integer> getSchemeAndPort(HttpClientRequest task) {
        URI theUrl = task.getUri();
        boolean isSecure = isSecure(task.getOverrideConfig());
        int port = -1;
        if (theUrl.getHost() != null) {
            port = theUrl.getPort();
        }
        if (port < 0 && !isSecure){
            port = 80;
        } else if (port < 0 && isSecure){
            port = 443;
        }
        String scheme = theUrl.getScheme();
        if (scheme == null){
            if (isSecure) {
                scheme = "https";
            } else {
                scheme = "http";
            }
        }
        return new Pair<String, Integer>(scheme, port);
    }

    @Override
    protected Pair<String, Integer> getHostAndPort(String vipAddress)  throws URISyntaxException {
        Pair<String, Integer> hostAndPort = new Pair<String, Integer>("",
                new Integer(80));
        if (!vipAddress.contains("http")){
            vipAddress = "http://" + vipAddress;
        }
        URI uri = new URI(vipAddress);
        if (uri.getHost() != null) {
            hostAndPort.setFirst(uri.getHost());
        }
        if (uri.getPort() != -1) {
            hostAndPort.setSecond(uri.getPort());
        }
        return hostAndPort;
    }

    private HttpClientResponse execute(Verb verb, URI uri,
            MultivaluedMap<String, String> headers, MultivaluedMap<String, String> params,
            NiwsClientConfig overriddenClientConfig, Object requestEntity) throws Exception {
        HttpClientResponse thisResponse = null;
        boolean bbFollowRedirects = bFollowRedirects;
        Server server = new Server(uri.getHost(), uri.getPort());
        // read overriden props
        if (overriddenClientConfig!=null){
            // set whether we should auto follow redirects
            if (overriddenClientConfig.getProperty(NiwsClientConfigKey.FollowRedirects)!=null){
                // use default directive from overall config
                Boolean followRedirects = Boolean.valueOf(""+overriddenClientConfig.getProperty(NiwsClientConfigKey.FollowRedirects, bFollowRedirects));
                bbFollowRedirects = followRedirects.booleanValue();
            }
        }
        restClient.setFollowRedirects(bbFollowRedirects);

        if (logger.isDebugEnabled()) {
            logger.debug("RestClient sending new Request(" + verb
                    + ": ) " + uri);
        }


        WebResource xResource = restClient.resource(uri.toString());
        ClientResponse jerseyResponse;

        Builder b = xResource.getRequestBuilder();

        if (headers != null) {
            Iterator<String> it = headers.keySet().iterator();
            while (it.hasNext()) {
                String name = it.next();
                String value = headers.getFirst(name);
                b = b.header(name, value);
            }
        }
        int readTimeout = getReadTimeout(verb.toString(), uri);
        restClient.setReadTimeout(readTimeout);
        if (httpClient4 != null) {
            HttpConnectionParams.setSoTimeout(httpClient4.getParams(), readTimeout);
        }
        switch (verb) {
        case GET:
            jerseyResponse = b.get(ClientResponse.class);
            break;
        case POST:
            jerseyResponse = b.post(ClientResponse.class, requestEntity);
            break;
        case PUT:
            jerseyResponse = b.put(ClientResponse.class, requestEntity);
            break;
        case DELETE:
            jerseyResponse = b.delete(ClientResponse.class, requestEntity);
            break;
        case HEAD:
            jerseyResponse = b.head();
            break;
        case OPTIONS:
            jerseyResponse = b.options(ClientResponse.class);
            break;
        default:
            throw new NIWSClientException(
                    NIWSClientException.ErrorType.GENERAL,
                    "You have to one of the REST verbs such as GET, POST etc.");
        }

        thisResponse = new HttpClientResponse(jerseyResponse);
        thisResponse.setRequestedURI(uri);
        if (thisResponse.getStatus() == 503){
            throw new NIWSClientException(NIWSClientException.ErrorType.SERVER_THROTTLED);
        }
        return thisResponse;
    }

    @Override
    protected boolean isRetriableException(Exception e) {
        boolean shouldRetry = isConnectException(e) || isSocketException(e);
        if (e instanceof NIWSClientException 
                && ((NIWSClientException)e).getErrorType() == NIWSClientException.ErrorType.SERVER_THROTTLED){
            shouldRetry = true;
        }
        return shouldRetry;
    }
    
    @Override
    protected boolean isCircuitBreakerException(Exception e) {
        return isConnectException(e) || isSocketException(e);
    }

    private static boolean isSocketException(Throwable e) {
        int levelCount = 0;
        while (e != null && levelCount < 10) {            
            if (e instanceof SocketException) { 
                return true;
            }
            e = e.getCause();
            levelCount++;
        }
        return false;
        
    }

    private static boolean isConnectException(Throwable e) {
        int levelCount = 0;
        while (e != null && levelCount < 10) {            
            if ((e instanceof SocketException) 
                    || ((e instanceof org.apache.http.conn.ConnectTimeoutException)
                            && !(e instanceof org.apache.http.conn.ConnectionPoolTimeoutException))) {
                return true;
            }
            e = e.getCause();
            levelCount++;
        }
        return false;
    }
    
    public int getReadTimeout(String httpMethod, URI uri) {
        int readTimeout = readTimeoutFastProperty.get();
        try {
            // check if we have a Method + URI specific override
            Map<String, HttpVerbUriRegexPropertyValue> dynamicPropMap = ncc
                    .getMethodURIConfigMap();
            if (dynamicPropMap != null) {
                String maxPropertyName = "";
                for (String key : dynamicPropMap.keySet()) {
                    // the key in the map is the alias. Let's check if a
                    // ReadTimeout has been configured for that alias
                    DynamicProperty fp = DynamicProperty.getInstance(NiwsClientConfig.getInstancePropName(restClientName,
                                    new StringBuilder(key)
                                            .append(".")
                                            .append(NiwsClientConfigKey.ReadTimeout)
                                            .toString()));
                    Integer readTimeoutFromProp = fp
                            .getInteger(Integer.MIN_VALUE);
                    if (readTimeoutFromProp.intValue() != Integer.MIN_VALUE) {
                        HttpVerbUriRegexPropertyValue value = dynamicPropMap
                            .get(key);
                        if (value.getVerb() == HttpVerbUriRegexPropertyValue.Verb.ANY_VERB
                                || HttpVerbUriRegexPropertyValue.Verb
                                        .get(httpMethod) == value.getVerb()) {
                            if (uri.toString().matches(value.getUriRegex())) {
                                // if the property key name is greater (comes
                                // last in alphabetical order) than any previous
                                // property names for same method/uri combination, 
                                // then take the read timeout value for this 
                                // property key name
                                if (maxPropertyName.equals("")
                                        || key.compareTo(maxPropertyName) > 0) {
                                    maxPropertyName = key;
                                    readTimeout = readTimeoutFromProp
                                            .intValue();
                                }
                            }
                        }
                    }

                }
            }
        } catch (Throwable t) {
            logger.warn("Encountered exception while figuring out Method URI "
                    + "based read timeout, will fallback to value for "
                    + "the restclient as a whole (" + readTimeout + ")", t);
        }
        return readTimeout;
    }


}
