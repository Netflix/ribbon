package com.netflix.ribbonclientextensions;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Multimap;
import com.netflix.ribbonclientextensions.template.TemplateParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

@SuppressWarnings("UnusedDeclaration")
public class Resource<Result>   {

    private static final Logger logger = LoggerFactory.getLogger(Resource.class);

    final ResourceTemplate<Result> resourceTemplate;

    Map<String, String> targetMap = null;
    StringBuilder queryParams;
    Object entity;
    String primaryCacheKey;
    String secondaryCacheKey;
    CustomerContext customerContext;



    /**
     * @param resourceTemplate the resourceTemplate for this target.
     */
    protected Resource(ResourceTemplate<Result> resourceTemplate) {
        this.resourceTemplate = resourceTemplate;
         targetMap = new HashMap<String, String>();
    }


    /**
     * specified the one of the path targets of the URI : /ribbonclientextensions/{targetName1}/{targetName2}/{;targetName3}...
     *
     * @param targetName  name of the target i.e. targetName of {targetName} in a resourceTemplate's URI.
     * @param targetValue value to replace {targetName} with.
     * @return Builder
     */
    public Resource<Result> withTarget(String targetName, Object targetValue) {
        targetMap.put(targetName, URLEncode(targetValue.toString()));
        return this;
    }

    /**
     * There are times when you need to double encode your path target.  Specifically if the target includes "/" or "\"
     * specified the one of the path targets of the URI : /ribbonclientextensions/{target1}/{target2}/...
     *
     * @param targetName  name of the target i.e. target1 of {target1} above.
     * @param targetValue value placed here.
     * @return Builder
     */
    public Resource<Result> withDoubleEncodedTarget(String targetName, Object targetValue) {
        return withTarget(targetName, URLEncode(targetValue.toString()));
    }

    /**
     * A true REST api does not have query parameters. It is recommended that either the target ribbonclientextensions be defined
     * in a way you don't need a parameter or make use of matrix parameters
     *
     * @param name  parameter name
     * @param value parameter value
     * @return Builder
     */
    public Resource<Result> withQueryParameter(String name, String value) {
        if (value != null) {
            if (queryParams == null) {
                queryParams = new StringBuilder();
            } else {
                queryParams.append('&');
            }
            queryParams.append(name)
                    .append('=')
                    .append(URLEncode(value));
        }
        return this;
    }

    /**
     * There are times when you need to double encode your path target.
     * A true REST api does not have query parameters. It is recommended that either the target ribbonclientextensions be defined
     * in a way you don't need a parameter or make use of matrix parameters
     *
     * @param name  parameter name
     * @param value parameter value
     * @return Builder
     */
    public Resource<Result> withDoubleEncodedQueryParameter(String name, String value) {
        withQueryParameter(name, URLEncode(value));
        return this;
    }


    /**
     * Send an entity along. normally used for PUT and POST requests.
     *
     * @param entity entity to send.
     * @return Builder
     */
    public Resource<Result> withEntity(Object entity) {
        this.entity = entity;
        return this;
    }


/*
    public Resource<Result> withSniperMonkey(SniperMonkey sniperMonkey) {
        this.sniperMonkey = sniperMonkey;
        return this;

    }

    public SniperMonkey getSniperMonkey() {
        return sniperMonkey;
    }


*/



    /**
     * build the target.
     *
     * @return Builder
     */
    public URI toURI() throws URISyntaxException {
        StringBuilder uri = new StringBuilder(resourceTemplate.toURIFragment(targetMap));

        //  Bad form to have query params in a REST call.
        if (queryParams != null && queryParams.length() > 0) {
            uri.append('?').append(queryParams);
        }

        try {
            return new URI(uri.toString());
        } catch (URISyntaxException e) {
            logger.error(String.format("%s -- uri: '%s'", e.getMessage(), uri.toString()), e);
            throw e;
        }

    }

    private String URLEncode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return URLEncoder.encode(value.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.error("Could not encode: %s", value, e);
            return value.toString();
        }
    }



    public ResourceTemplate<Result> getResourceTemplate() {
        return resourceTemplate;
    }

    public Object getEntity() {
        return entity;
    }



    public void addHeader(String name, String value) {
        //todo
    }


    public Resource<Result> withCustomerContext(CustomerContext customerContext) {
        this.customerContext = customerContext;
        return this;
    }

    public String getCacheKey() throws URISyntaxException {
        if(resourceTemplate.primaryCacheConfig.cacheKeyTemplate == null) return "";
        return TemplateParser.toData(targetMap,
                resourceTemplate.primaryCacheConfig.cacheKeyTemplate,
                resourceTemplate.parsedList);
    }

    public Observable<Response<Result>> execute() {
        return null;
    }

    public Multimap<String,String> getHeaders() {
        return resourceTemplate.httpService.getHeaders(); // todo ( include all headers  from template and resource)
    }
}
