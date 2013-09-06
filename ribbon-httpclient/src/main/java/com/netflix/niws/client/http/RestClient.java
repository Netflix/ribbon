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
package com.netflix.niws.client.http;

import java.io.File;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.KeyStore;
import java.util.Iterator;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.AbstractLoadBalancerAwareClient;
import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.http4.NFHttpClient;
import com.netflix.http4.NFHttpClientConstants;
import com.netflix.http4.NFHttpClientFactory;
import com.netflix.http4.NFHttpMethodRetryHandler;
import com.netflix.http4.ssl.KeyStoreAwareSocketFactory;
import com.netflix.niws.cert.AbstractSslContextFactory;
import com.netflix.niws.client.ClientSslSocketFactoryException;
import com.netflix.niws.client.URLSslContextFactory;
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

/**
 * A client that is essentially a wrapper around Jersey client. By default, it uses HttpClient for underlying HTTP communication.
 * Application can set its own Jersey client with this class, but doing so will void all client configurations set in {@link IClientConfig}.
 *
 * @author awang
 *
 */
public class RestClient extends AbstractLoadBalancerAwareClient<HttpClientRequest, HttpClientResponse> {

    private Client restClient;
    private HttpClient httpClient4;
    private IClientConfig ncc;
    private String restClientName;

    private boolean enableConnectionPoolCleanerTask = false;
    private DynamicIntProperty connIdleEvictTimeMilliSeconds;
    private int connectionCleanerRepeatInterval;
    private int maxConnectionsperHost;
    private int maxTotalConnections;
    private int connectionTimeout;
    private int readTimeout;
    private String proxyHost;
    private int proxyPort;
    private boolean isSecure;
    private boolean isHostnameValidationRequired;
    private boolean isClientAuthRequired;
    private boolean ignoreUserToken;
    private ApacheHttpClient4Config config;

    boolean bFollowRedirects = DefaultClientConfigImpl.DEFAULT_FOLLOW_REDIRECTS;

    private static final Logger logger = LoggerFactory.getLogger(RestClient.class);

    public RestClient() {
        restClientName = "default";
    }

    public RestClient(IClientConfig ncc) {
        initWithNiwsConfig(ncc);
    }

    public RestClient(Client jerseyClient) {
        this.restClient = jerseyClient;
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        super.initWithNiwsConfig(clientConfig);
        this.ncc = clientConfig;
        this.restClientName = ncc.getClientName();
        this.isSecure = getBooleanFromConfig(ncc, CommonClientConfigKey.IsSecure, this.isSecure);
        this.isHostnameValidationRequired = getBooleanFromConfig(ncc, CommonClientConfigKey.IsHostnameValidationRequired, this.isHostnameValidationRequired);
        this.isClientAuthRequired = getBooleanFromConfig(ncc, CommonClientConfigKey.IsClientAuthRequired, this.isClientAuthRequired);
        this.bFollowRedirects = getBooleanFromConfig(ncc, CommonClientConfigKey.FollowRedirects, true);
        this.ignoreUserToken = getBooleanFromConfig(ncc, CommonClientConfigKey.IgnoreUserTokenInConnectionPoolForSecureClient, this.ignoreUserToken);

        this.config = new DefaultApacheHttpClient4Config();
        this.config.getProperties().put(
                ApacheHttpClient4Config.PROPERTY_CONNECT_TIMEOUT,
                Integer.parseInt(String.valueOf(ncc.getProperty(CommonClientConfigKey.ConnectTimeout))));
        this.config.getProperties().put(
                ApacheHttpClient4Config.PROPERTY_READ_TIMEOUT,
                Integer.parseInt(String.valueOf(ncc.getProperty(CommonClientConfigKey.ReadTimeout))));

        this.restClient = apacheHttpClientSpecificInitialization();
    }

    protected Client apacheHttpClientSpecificInitialization() {
        httpClient4 = NFHttpClientFactory.getNamedNFHttpClient(restClientName, true);

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
            enableConnectionPoolCleanerTask = Boolean.parseBoolean(ncc.getProperty(CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled,
                    NFHttpClientConstants.DEFAULT_CONNECTIONIDLE_TIMETASK_ENABLED).toString());
            nfHttpClient.getConnPoolCleaner().setEnableConnectionPoolCleanerTask(enableConnectionPoolCleanerTask);
        } catch (Exception e1) {
            throw new IllegalArgumentException("Invalid value for property:"
                    + CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled, e1);
        }
        if (enableConnectionPoolCleanerTask) {
            try {
                connectionCleanerRepeatInterval = Integer
                .parseInt(String.valueOf(ncc.getProperty(CommonClientConfigKey.ConnectionCleanerRepeatInterval,
                        NFHttpClientConstants.DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS)));
                nfHttpClient.getConnPoolCleaner().setConnectionCleanerRepeatInterval(connectionCleanerRepeatInterval);
            } catch (Exception e1) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + CommonClientConfigKey.ConnectionCleanerRepeatInterval, e1);
            }

            try {
                int iConnIdleEvictTimeMilliSeconds = Integer
                .parseInt(""
                        + ncc
                        .getProperty(CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds,
                                NFHttpClientConstants.DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS));
                connIdleEvictTimeMilliSeconds = DynamicPropertyFactory.getInstance().getIntProperty(
                        restClientName
                        + ".nfhttpclient.connIdleEvictTimeMilliSeconds",
                        iConnIdleEvictTimeMilliSeconds);
                nfHttpClient.setConnIdleEvictTimeMilliSeconds(connIdleEvictTimeMilliSeconds);
            } catch (Exception e1) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds,
                        e1);
            }

            nfHttpClient.initConnectionCleanerTask();
        }

        try {
            maxConnectionsperHost = Integer
            .parseInt(""
                    + ncc
                    .getProperty(CommonClientConfigKey.MaxHttpConnectionsPerHost,
                            maxConnectionsperHost));
            ClientConnectionManager connMgr = httpClient4.getConnectionManager();
            if (connMgr instanceof ThreadSafeClientConnManager) {
                ((ThreadSafeClientConnManager) connMgr)
                .setDefaultMaxPerRoute(maxConnectionsperHost);
            }
        } catch (Exception e1) {
            throw new IllegalArgumentException("Invalid value for property:"
                    + CommonClientConfigKey.MaxHttpConnectionsPerHost, e1);
        }

        try {
            maxTotalConnections = Integer
            .parseInt(""
                    + ncc
                    .getProperty(CommonClientConfigKey.MaxTotalHttpConnections,
                            maxTotalConnections));
            ClientConnectionManager connMgr = httpClient4
            .getConnectionManager();
            if (connMgr instanceof ThreadSafeClientConnManager) {
                ((ThreadSafeClientConnManager) connMgr)
                .setMaxTotal(maxTotalConnections);
            }
        } catch (Exception e1) {
            throw new IllegalArgumentException("Invalid value for property:"
                    + CommonClientConfigKey.MaxTotalHttpConnections, e1);
        }

        try {
            connectionTimeout = Integer.parseInt(""
                    + ncc.getProperty(CommonClientConfigKey.ConnectTimeout,
                            connectionTimeout));
            HttpConnectionParams.setConnectionTimeout(httpClientParams,
                    connectionTimeout);
        } catch (Exception e1) {
            throw new IllegalArgumentException("Invalid value for property:"
                    + CommonClientConfigKey.ConnectTimeout, e1);
        }

        try {
            readTimeout = Integer.parseInt(""
                    + ncc.getProperty(CommonClientConfigKey.ReadTimeout,
                            readTimeout));
            HttpConnectionParams.setSoTimeout(httpClientParams, readTimeout);
        } catch (Exception e1) {
            throw new IllegalArgumentException("Invalid value for property:"
                    + CommonClientConfigKey.ReadTimeout, e1);
        }

        // httpclient 4 seems to only have one buffer size controlling both
        // send/receive - so let's take the bigger of the two values and use
        // it as buffer size
        int bufferSize = Integer.MIN_VALUE;
        if (ncc.getProperty(CommonClientConfigKey.ReceiveBuffferSize) != null) {
            try {
                bufferSize = Integer
                .parseInt(""
                        + ncc
                        .getProperty(CommonClientConfigKey.ReceiveBuffferSize));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + CommonClientConfigKey.ReceiveBuffferSize,
                        e);
            }
            if (ncc.getProperty(CommonClientConfigKey.SendBufferSize) != null) {
                try {
                    int sendBufferSize = Integer
                    .parseInt(""
                            + ncc
                            .getProperty(CommonClientConfigKey.SendBufferSize));
                    if (sendBufferSize > bufferSize) {
                        bufferSize = sendBufferSize;
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Invalid value for property:"
                            + CommonClientConfigKey.SendBufferSize,
                            e);
                }
            }
        }
        if (bufferSize != Integer.MIN_VALUE) {
            HttpConnectionParams.setSocketBufferSize(httpClientParams,
                    bufferSize);
        }

        if (ncc.getProperty(CommonClientConfigKey.StaleCheckingEnabled) != null) {
            try {
                HttpConnectionParams
                .setStaleCheckingEnabled(httpClientParams,
                        Boolean.parseBoolean(ncc.getProperty(CommonClientConfigKey.StaleCheckingEnabled, false).toString()));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + CommonClientConfigKey.StaleCheckingEnabled,
                        e);
            }
        }

        if (ncc.getProperty(CommonClientConfigKey.Linger) != null) {
            try {
                HttpConnectionParams.setLinger(httpClientParams,
                        Integer.parseInt(""
                                + ncc.getProperty(CommonClientConfigKey.Linger)));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + CommonClientConfigKey.Linger,
                        e);
            }
        }

        if (ncc.getProperty(CommonClientConfigKey.ProxyHost) != null) {
            try {
                proxyHost = (String) ncc
                .getProperty(CommonClientConfigKey.ProxyHost);
                proxyPort = Integer.parseInt(""
                        + ncc.getProperty(CommonClientConfigKey.ProxyPort));
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                httpClient4
                .getParams()
                .setParameter(ConnRouteParams.DEFAULT_PROXY, proxy);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid value for property:"
                        + CommonClientConfigKey.ProxyHost,
                        e);
            }
        }

        if (isSecure) {
            final URL trustStoreUrl = getResourceForOptionalProperty(CommonClientConfigKey.TrustStore);
            final URL keyStoreUrl = getResourceForOptionalProperty(CommonClientConfigKey.KeyStore);

        	final ClientConnectionManager currentManager = httpClient4.getConnectionManager();

            AbstractSslContextFactory abstractFactory = null;


            if (    // if client auth is required, need both a truststore and a keystore to warrant configuring
            		// if client is not is not required, we only need a keystore OR a truststore to warrant configuring
            		(isClientAuthRequired && (trustStoreUrl != null && keyStoreUrl != null))
            		||
            		(!isClientAuthRequired && (trustStoreUrl != null || keyStoreUrl != null))
            		) {

                try {
                	abstractFactory = new URLSslContextFactory(trustStoreUrl,
                            (String) ncc.getProperty(CommonClientConfigKey.TrustStorePassword),
                            keyStoreUrl,
                            (String) ncc.getProperty(CommonClientConfigKey.KeyStorePassword));

                } catch (ClientSslSocketFactoryException e) {
                    throw new IllegalArgumentException("Unable to configure custom secure socket factory", e);
                }
            }

        	KeyStoreAwareSocketFactory awareSocketFactory;
        	try {
        		awareSocketFactory = isHostnameValidationRequired ? new KeyStoreAwareSocketFactory(abstractFactory) :
        		    new KeyStoreAwareSocketFactory(abstractFactory, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

        		currentManager.getSchemeRegistry().register(new Scheme(
                        "https",443, awareSocketFactory));

        	} catch (Exception e) {
        		throw new IllegalArgumentException("Unable to configure custom secure socket factory", e);
        	}
        }

        // Warning that if user tokens are used (i.e. ignoreUserToken == false) this may be prevent SSL connections from being
        // reused, which is generally not the intent for long-living proxy connections and the like.
        // See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/advanced.html
        if (ignoreUserToken) {
            ((DefaultHttpClient) httpClient4).setUserTokenHandler(new UserTokenHandler() {
                @Override
                public Object getUserToken(HttpContext context) {
                    return null;
                }
            });
        }

        ApacheHttpClient4Handler handler = new ApacheHttpClient4Handler(httpClient4, new BasicCookieStore(), false);

        return new ApacheHttpClient4(handler, config);
    }

    public void resetSSLSocketFactory(AbstractSslContextFactory abstractContextFactory){

    	try {

    		KeyStoreAwareSocketFactory awareSocketFactory = isHostnameValidationRequired ? new KeyStoreAwareSocketFactory(abstractContextFactory) :
                new KeyStoreAwareSocketFactory(abstractContextFactory, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    		httpClient4.getConnectionManager().getSchemeRegistry().register(new Scheme(
                    "https",443, awareSocketFactory));

    	} catch (Exception e) {
    		throw new IllegalArgumentException("Unable to configure custom secure socket factory", e);
    	}
    }

    public KeyStore getKeyStore(){

    	SchemeRegistry registry = httpClient4.getConnectionManager().getSchemeRegistry();

    	if(! registry.getSchemeNames().contains("https")){
    		throw new IllegalStateException("Registry does not include an 'https' entry.");
    	}

    	SchemeSocketFactory awareSocketFactory = httpClient4.getConnectionManager().getSchemeRegistry().getScheme("https").getSchemeSocketFactory();

    	if(awareSocketFactory instanceof KeyStoreAwareSocketFactory){
    		return ((KeyStoreAwareSocketFactory) awareSocketFactory).getKeyStore();
    	}else{
    		throw new IllegalStateException("Cannot extract keystore from scheme socket factory of type: " + awareSocketFactory.getClass().getName());
    	}
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
            	logger.error("Problem loading resource", e);
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

    private URL getResourceForOptionalProperty(final IClientConfigKey configKey) {
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


    private boolean getBooleanFromConfig(IClientConfig overriddenClientConfig, IClientConfigKey key, boolean defaultValue){

    	if(overriddenClientConfig != null && overriddenClientConfig.containsProperty(key)){
    		defaultValue = Boolean.parseBoolean(overriddenClientConfig.getProperty(key).toString());
    	}

    	return defaultValue;

    }

	@Override
	protected int getDefaultPort() {
		return 80;
	}



    @Override
    protected int getDefaultPortFromScheme(String scheme) {
        int port = super.getDefaultPortFromScheme(scheme);
        if (port < 0) {
            return 80;
        } else {
            return port;
        }
    }

    @Override
    protected Pair<String, Integer> deriveSchemeAndPortFromPartialUri(HttpClientRequest task) {
        URI theUrl = task.getUri();
        boolean isSecure = getBooleanFromConfig(task.getOverrideConfig(), CommonClientConfigKey.IsSecure, this.isSecure);
        String scheme = theUrl.getScheme();
        if (scheme != null) {
            isSecure = 	scheme.equalsIgnoreCase("https");
        }
        int port = theUrl.getPort();
        if (port < 0 && !isSecure){
            port = 80;
        } else if (port < 0 && isSecure){
            port = 443;
        }
        if (scheme == null){
            if (isSecure) {
                scheme = "https";
            } else {
                scheme = "http";
            }
        }
        return new Pair<String, Integer>(scheme, port);
    }

    private HttpClientResponse execute(Verb verb, URI uri,
            MultivaluedMap<String, String> headers, MultivaluedMap<String, String> params,
            IClientConfig overriddenClientConfig, Object requestEntity) throws Exception {
        HttpClientResponse thisResponse = null;
        boolean bbFollowRedirects = bFollowRedirects;
        // read overriden props
        if (overriddenClientConfig != null
        		// set whether we should auto follow redirects
        		&& overriddenClientConfig.getProperty(CommonClientConfigKey.FollowRedirects)!=null){
        	// use default directive from overall config
        	Boolean followRedirects = Boolean.valueOf(""+overriddenClientConfig.getProperty(CommonClientConfigKey.FollowRedirects, bFollowRedirects));
        	bbFollowRedirects = followRedirects.booleanValue();
        }
        restClient.setFollowRedirects(bbFollowRedirects);

        if (logger.isDebugEnabled()) {
            logger.debug("RestClient sending new Request(" + verb
                    + ": ) " + uri);
        }


        WebResource xResource = restClient.resource(uri.toString());
        if (params != null) {
        	xResource = xResource.queryParams(params);
        }
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
            throw new ClientException(
                    ClientException.ErrorType.GENERAL,
                    "You have to one of the REST verbs such as GET, POST etc.");
        }

        thisResponse = new HttpClientResponse(jerseyResponse);
        thisResponse.setRequestedURI(uri);
        if (thisResponse.getStatus() == 503){
            throw new ClientException(ClientException.ErrorType.SERVER_THROTTLED);
        }
        return thisResponse;
    }

    @Override
    protected boolean isRetriableException(Exception e) {
        boolean shouldRetry = isConnectException(e) || isSocketException(e);
        if (e instanceof ClientException
                && ((ClientException)e).getErrorType() == ClientException.ErrorType.SERVER_THROTTLED){
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

	@Override
	protected Pair<String, Integer> deriveHostAndPortFromVipAddress(String vipAddress)
			throws URISyntaxException, ClientException {
		if (!vipAddress.contains("http")) {
			vipAddress = "http://" + vipAddress;
		}
		return super.deriveHostAndPortFromVipAddress(vipAddress);
	}
}
