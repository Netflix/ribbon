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
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.KeyStore;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.netflix.client.config.Property;
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
import com.netflix.client.ClientFactory;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.ssl.AbstractSslContextFactory;
import com.netflix.client.ssl.ClientSslSocketFactoryException;
import com.netflix.client.ssl.URLSslContextFactory;
import com.netflix.http4.NFHttpClient;
import com.netflix.http4.NFHttpClientFactory;
import com.netflix.http4.NFHttpMethodRetryHandler;
import com.netflix.http4.ssl.KeyStoreAwareSocketFactory;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
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
 * @deprecated Please see ribbon-rxnetty module for the Netty based client. 
 *
 * @author awang
 *
 */
@Deprecated
public class RestClient extends AbstractLoadBalancerAwareClient<HttpRequest, HttpResponse> {

    private static IClientConfigKey<Integer> CONN_IDLE_EVICT_TIME_MILLIS = new CommonClientConfigKey<Integer>(
            "%s.nfhttpclient.connIdleEvictTimeMilliSeconds") {};


    private Client restClient;
    private HttpClient httpClient4;
    private IClientConfig ncc;
    private String restClientName;

    private boolean enableConnectionPoolCleanerTask = false;
    private Property<Integer> connIdleEvictTimeMilliSeconds;
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

    boolean bFollowRedirects = CommonClientConfigKey.FollowRedirects.defaultValue();

    private static final Logger logger = LoggerFactory.getLogger(RestClient.class);
    
    public RestClient() {
        super(null);
    }
    
    public RestClient(ILoadBalancer lb) {
        super(lb);
        restClientName = "default";
    }

    public RestClient(ILoadBalancer lb, IClientConfig ncc) {
        super(lb, ncc);
        initWithNiwsConfig(ncc);
    }

    public RestClient(IClientConfig ncc) {
        super(null, ncc);
        initWithNiwsConfig(ncc);
    }
    
    public RestClient(ILoadBalancer lb, Client jerseyClient) {
        super(lb);
        this.restClient = jerseyClient;
        this.setRetryHandler(new HttpClientLoadBalancerErrorHandler());
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        super.initWithNiwsConfig(clientConfig);
        this.ncc = clientConfig;
        this.restClientName = ncc.getClientName();
        this.isSecure = ncc.get(CommonClientConfigKey.IsSecure, this.isSecure);
        this.isHostnameValidationRequired = ncc.get(CommonClientConfigKey.IsHostnameValidationRequired, this.isHostnameValidationRequired);
        this.isClientAuthRequired = ncc.get(CommonClientConfigKey.IsClientAuthRequired, this.isClientAuthRequired);
        this.bFollowRedirects = ncc.get(CommonClientConfigKey.FollowRedirects, true);
        this.ignoreUserToken = ncc.get(CommonClientConfigKey.IgnoreUserTokenInConnectionPoolForSecureClient, this.ignoreUserToken);

        this.config = new DefaultApacheHttpClient4Config();
        this.config.getProperties().put(
                ApacheHttpClient4Config.PROPERTY_CONNECT_TIMEOUT,
                ncc.get(CommonClientConfigKey.ConnectTimeout));
        this.config.getProperties().put(
                ApacheHttpClient4Config.PROPERTY_READ_TIMEOUT,
                ncc.get(CommonClientConfigKey.ReadTimeout));

        this.restClient = apacheHttpClientSpecificInitialization();
        this.setRetryHandler(new HttpClientLoadBalancerErrorHandler(ncc));
    }

    private void throwInvalidValue(IClientConfigKey<?> key, Exception e) {
        throw new IllegalArgumentException("Invalid value for property:" + key, e);

    }

    protected Client apacheHttpClientSpecificInitialization() {
        httpClient4 = NFHttpClientFactory.getNamedNFHttpClient(restClientName, this.ncc, true);

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
            enableConnectionPoolCleanerTask = ncc.getOrDefault(CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled);
            nfHttpClient.getConnPoolCleaner().setEnableConnectionPoolCleanerTask(enableConnectionPoolCleanerTask);
        } catch (Exception e1) {
            throwInvalidValue(CommonClientConfigKey.ConnectionPoolCleanerTaskEnabled, e1);
        }
        if (enableConnectionPoolCleanerTask) {
            try {
                connectionCleanerRepeatInterval = ncc.getOrDefault(CommonClientConfigKey.ConnectionCleanerRepeatInterval);
                nfHttpClient.getConnPoolCleaner().setConnectionCleanerRepeatInterval(connectionCleanerRepeatInterval);
            } catch (Exception e1) {
                throwInvalidValue(CommonClientConfigKey.ConnectionCleanerRepeatInterval, e1);
            }

            try {
                connIdleEvictTimeMilliSeconds = ncc.getDynamicProperty(CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds);
                nfHttpClient.setConnIdleEvictTimeMilliSeconds(connIdleEvictTimeMilliSeconds);
            } catch (Exception e1) {
                throwInvalidValue(CommonClientConfigKey.ConnIdleEvictTimeMilliSeconds, e1);
            }

            nfHttpClient.initConnectionCleanerTask();
        }

        try {
            maxConnectionsperHost = ncc.getOrDefault(CommonClientConfigKey.MaxHttpConnectionsPerHost);
            ClientConnectionManager connMgr = httpClient4.getConnectionManager();
            if (connMgr instanceof ThreadSafeClientConnManager) {
                ((ThreadSafeClientConnManager) connMgr)
                .setDefaultMaxPerRoute(maxConnectionsperHost);
            }
        } catch (Exception e1) {
            throwInvalidValue(CommonClientConfigKey.MaxHttpConnectionsPerHost, e1);
        }

        try {
            maxTotalConnections = ncc.getOrDefault(CommonClientConfigKey.MaxTotalHttpConnections);
            ClientConnectionManager connMgr = httpClient4.getConnectionManager();
            if (connMgr instanceof ThreadSafeClientConnManager) {
                ((ThreadSafeClientConnManager) connMgr)
                .setMaxTotal(maxTotalConnections);
            }
        } catch (Exception e1) {
            throwInvalidValue(CommonClientConfigKey.MaxTotalHttpConnections, e1);
        }

        try {
            connectionTimeout = ncc.getOrDefault(CommonClientConfigKey.ConnectTimeout);
            HttpConnectionParams.setConnectionTimeout(httpClientParams,
                    connectionTimeout);
        } catch (Exception e1) {
            throwInvalidValue(CommonClientConfigKey.ConnectTimeout, e1);
        }

        try {
            readTimeout = ncc.getOrDefault(CommonClientConfigKey.ReadTimeout);
            HttpConnectionParams.setSoTimeout(httpClientParams, readTimeout);
        } catch (Exception e1) {
            throwInvalidValue(CommonClientConfigKey.ReadTimeout, e1);
        }

        // httpclient 4 seems to only have one buffer size controlling both
        // send/receive - so let's take the bigger of the two values and use
        // it as buffer size
        int bufferSize = Integer.MIN_VALUE;
        if (ncc.get(CommonClientConfigKey.ReceiveBufferSize) != null) {
            try {
                bufferSize = ncc.getOrDefault(CommonClientConfigKey.ReceiveBufferSize);
            } catch (Exception e) {
                throwInvalidValue(CommonClientConfigKey.ReceiveBufferSize, e);
            }
            if (ncc.get(CommonClientConfigKey.SendBufferSize) != null) {
                try {
                    int sendBufferSize = ncc.getOrDefault(CommonClientConfigKey.SendBufferSize);
                    if (sendBufferSize > bufferSize) {
                        bufferSize = sendBufferSize;
                    }
                } catch (Exception e) {
                    throwInvalidValue(CommonClientConfigKey.SendBufferSize,e);
                }
            }
        }
        if (bufferSize != Integer.MIN_VALUE) {
            HttpConnectionParams.setSocketBufferSize(httpClientParams,
                    bufferSize);
        }

        if (ncc.get(CommonClientConfigKey.StaleCheckingEnabled) != null) {
            try {
                HttpConnectionParams.setStaleCheckingEnabled(
                        httpClientParams, ncc.getOrDefault(CommonClientConfigKey.StaleCheckingEnabled));
            } catch (Exception e) {
                throwInvalidValue(CommonClientConfigKey.StaleCheckingEnabled, e);
            }
        }

        if (ncc.get(CommonClientConfigKey.Linger) != null) {
            try {
                HttpConnectionParams.setLinger(httpClientParams, ncc.getOrDefault(CommonClientConfigKey.Linger));
            } catch (Exception e) {
                throwInvalidValue(CommonClientConfigKey.Linger, e);
            }
        }

        if (ncc.get(CommonClientConfigKey.ProxyHost) != null) {
            try {
                proxyHost = (String) ncc.getOrDefault(CommonClientConfigKey.ProxyHost);
                proxyPort = ncc.getOrDefault(CommonClientConfigKey.ProxyPort);
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                httpClient4.getParams()
                    .setParameter(ConnRouteParams.DEFAULT_PROXY, proxy);
            } catch (Exception e) {
                throwInvalidValue(CommonClientConfigKey.ProxyHost, e);
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
            		    || (!isClientAuthRequired && (trustStoreUrl != null || keyStoreUrl != null))
            		) {

                try {
                	abstractFactory = new URLSslContextFactory(trustStoreUrl,
                            ncc.get(CommonClientConfigKey.TrustStorePassword),
                            keyStoreUrl,
                            ncc.get(CommonClientConfigKey.KeyStorePassword));

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

        // custom SSL Factory handler
        String customSSLFactoryClassName = ncc.get(CommonClientConfigKey.CustomSSLSocketFactoryClassName);

        if (customSSLFactoryClassName != null){
            try{
                SSLSocketFactory customSocketFactory = (SSLSocketFactory) ClientFactory.instantiateInstanceWithClientConfig(customSSLFactoryClassName, ncc);

                httpClient4.getConnectionManager().getSchemeRegistry().register(new Scheme(
                        "https",443, customSocketFactory));

            } catch(Exception e){
                throwInvalidValue(CommonClientConfigKey.CustomSSLSocketFactoryClassName, e);
            }
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
            url = RestClient.class.getResource(resourceName);
        }
        if (url == null) {
            // attempt to load from the system classpath
            url = RestClient.class.getClassLoader().getResource(resourceName);
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
        final String propValue = (String) ncc.get(configKey);
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

    public HttpResponse execute(HttpRequest task) throws Exception  {
        return execute(task, null);
    }
    
    @Override
    public HttpResponse execute(HttpRequest task, IClientConfig requestConfig) throws Exception {
        IClientConfig config = (requestConfig == null) ? task.getOverrideConfig() : requestConfig;
        return execute(task.getVerb(), task.getUri(),
                task.getHeaders(), task.getQueryParams(), config, task.getEntity());
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
    protected Pair<String, Integer> deriveSchemeAndPortFromPartialUri(URI uri) {
        boolean isSecure = ncc.get(CommonClientConfigKey.IsSecure, this.isSecure);
        String scheme = uri.getScheme();
        if (scheme != null) {
            isSecure = 	scheme.equalsIgnoreCase("https");
        }
        int port = uri.getPort();
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
        return new Pair<>(scheme, port);
    }

    private HttpResponse execute(HttpRequest.Verb verb, URI uri,
            Map<String, Collection<String>> headers, Map<String, Collection<String>> params,
            IClientConfig overriddenClientConfig, Object requestEntity) throws Exception {
        HttpClientResponse thisResponse = null;

        final boolean bbFollowRedirects = Optional.ofNullable(overriddenClientConfig)
                .flatMap(config -> config.getIfSet(CommonClientConfigKey.FollowRedirects))
                .orElse(bFollowRedirects);

        restClient.setFollowRedirects(bbFollowRedirects);

        if (logger.isDebugEnabled()) {
            logger.debug("RestClient sending new Request(" + verb
                    + ": ) " + uri);
        }


        WebResource xResource = restClient.resource(uri.toString());
        if (params != null) {
            for (Map.Entry<String, Collection<String>> entry: params.entrySet()) {
                String name = entry.getKey();
                for (String value: entry.getValue()) {
                    xResource = xResource.queryParam(name, value);
                }
            }
        }
        ClientResponse jerseyResponse;

        Builder b = xResource.getRequestBuilder();

        if (headers != null) {
            for (Map.Entry<String, Collection<String>> entry: headers.entrySet()) {
                String name = entry.getKey();
                for (String value: entry.getValue()) {
                    b = b.header(name, value);
                }
            }
        }
        Object entity = requestEntity;
        
        switch (verb) {
        case GET:
            jerseyResponse = b.get(ClientResponse.class);
            break;
        case POST:
            jerseyResponse = b.post(ClientResponse.class, entity);
            break;
        case PUT:
            jerseyResponse = b.put(ClientResponse.class, entity);
            break;
        case DELETE:
            jerseyResponse = b.delete(ClientResponse.class);
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

        thisResponse = new HttpClientResponse(jerseyResponse, uri, overriddenClientConfig);
        if (thisResponse.getStatus() == 503){
            thisResponse.close();
            throw new ClientException(ClientException.ErrorType.SERVER_THROTTLED);
        }
        return thisResponse;
    }

    @Override
    protected boolean isRetriableException(Throwable e) {
        if (e instanceof ClientException
                && ((ClientException)e).getErrorType() == ClientException.ErrorType.SERVER_THROTTLED){
            return false;
        }
        boolean shouldRetry = isConnectException(e) || isSocketException(e);
        return shouldRetry;
    }

    @Override
    protected boolean isCircuitBreakerException(Throwable e) {
        if (e instanceof ClientException) {
            ClientException clientException = (ClientException) e;
            if (clientException.getErrorType() == ClientException.ErrorType.SERVER_THROTTLED) {
                return true;
            }
        }
        return isConnectException(e) || isSocketException(e);
    }

    private static boolean isSocketException(Throwable e) {
        int levelCount = 0;
        while (e != null && levelCount < 10) {
            if ((e instanceof SocketException) || (e instanceof SocketTimeoutException)) {
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

    @Override
    public RequestSpecificRetryHandler getRequestSpecificRetryHandler(
            HttpRequest request, IClientConfig requestConfig) {
        if (!request.isRetriable()) {
            return new RequestSpecificRetryHandler(false, false, this.getRetryHandler(), requestConfig);
        }
        if (this.ncc.get(CommonClientConfigKey.OkToRetryOnAllOperations, false)) {
            return new RequestSpecificRetryHandler(true, true, this.getRetryHandler(), requestConfig);
        }
        if (request.getVerb() != HttpRequest.Verb.GET) {
            return new RequestSpecificRetryHandler(true, false, this.getRetryHandler(), requestConfig);
        } else {
            return new RequestSpecificRetryHandler(true, true, this.getRetryHandler(), requestConfig);
        } 
    }
	
	public void shutdown() {
	    ILoadBalancer lb = this.getLoadBalancer();
	    if (lb instanceof BaseLoadBalancer) {
	        ((BaseLoadBalancer) lb).shutdown();
	    }
	    NFHttpClientFactory.shutdownNFHttpClient(restClientName);
	}
}

