package com.netflix.ribbonclientextensions;

import com.netflix.client.http.HttpRequest;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;
import com.netflix.ribbonclientextensions.interfaces.CacheProvider;
import com.netflix.ribbonclientextensions.template.MatrixVar;
import com.netflix.ribbonclientextensions.template.PathVar;
import com.netflix.ribbonclientextensions.template.TemplateParser;
import com.netflix.ribbonclientextensions.template.TemplateVar;


import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @param <Result> The Class returned by a successful service call
 */
@SuppressWarnings("UnusedDeclaration")
public class ResourceTemplate<Result> {

    protected HttpService httpService;

    protected final List<CacheProvider<Result>> cacheProviders = new LinkedList<CacheProvider<Result>>();
    public CacheHandler primaryCacheHandler;
    public CacheConfig primaryCacheConfig;
    public CacheConfig secondaryCacheConfig;

    protected final Class<Result> resultClass;

    protected final HttpRequest.Verb verb;
    protected final String uriTemplate;


    protected FallbackHandler<Result> fallbackHandler;


    protected final List<Object> parsedList;
    protected String commandName;


    /**
     * Defines a REST Resource or endpoint. Is is expected that a HystrixRESTClient will define the expected successful
     * HTTP status codes as well as non successful HTTP status codes that occur under normal operation.
     * e.g. Authoritative errors such as 404 Not Found or 401 Unauthorized.  Fall back if used should never fall back for
     * successful or authoritative error status codes.
     *
     * @param verb        Verb for this call (GET, POST, PUT, etc)
     * @param resultClass the class to be returned by the call. (Need the class so we can create one)
     * @param uriTemplate the REST resourceTemplate to build URI fragments from.
     *
     */
    public ResourceTemplate(HttpRequest.Verb verb, Class<Result> resultClass, String uriTemplate) {
        this(verb, resultClass, uriTemplate, null);
    }

    /**
     * Defines a REST Resource or endpoint. Is is expected that a HystrixRESTClient will define the expected successful
     * HTTP status codes as well as non successful HTTP status codes that occur under normal operation.
     * e.g. Authoritative errors such as 404 Not Found or 401 Unauthorized.  Fall back if used should never fall back for
     * successful or authoritative error status codes.
     *
     * @param verb        Verb for this call (GET, POST, PUT, etc)
     * @param resultClass the class to be returned by the call. (Need the class so we can create one)
     * @param uriTemplate the REST resourceTemplate will be appended to the application context to product the uri.
     *                    template                                  possible URI
     *  Path parameter:
     *                    /app/v1/Customer/{custId}           ->   /app/v1/Customer/123456
     *  Path parameter and a matrix parameter:
     *                    /app/v1/Customer/{custId}{;member}  =>   /app/v1/Customer/123456;member=true
     *  Matrix parameters are optional:
     *                    /app/v1/Customer/{custId}{;member}  =>   /app/v1/Customer/123456
     *
     *
     *
     * @param def         a HttpService containing default values for the template.
     */
    public ResourceTemplate(HttpRequest.Verb verb, Class<Result> resultClass, String uriTemplate, HttpService def) {

        httpService = def;
        this.resultClass = resultClass;
        this.verb = verb;
        this.uriTemplate = cleanResource(uriTemplate);
        this.parsedList = TemplateParser.parseTemplate(uriTemplate);
        commandName = defaultCommandName();
    }

    /**
     * constructor for use only by @see com.netflix.ribbonclientextensions.Resource
     *
     * @param template ResourceTemplate to copy.
     */
    protected ResourceTemplate(ResourceTemplate<Result> template) {

        httpService = template.httpService;
        this.parsedList = Collections.unmodifiableList(template.parsedList);
        this.resultClass = template.resultClass;
        this.verb = template.verb;
        this.uriTemplate = template.uriTemplate;
        this.commandName = template.commandName;

        // might be modified make a copy.
        this.cacheProviders.addAll(template.cacheProviders);
    }


    protected String defaultCommandName() {
        String name;
        name = this.verb + this.uriTemplate;
        // normalize for property file
        name = name.replaceAll("/", "_");
        name = name.replaceAll("[{}]", "-");
        return name;
    }


    protected String cleanResource(String resourceTemplate) {
        if (resourceTemplate != null) {
            resourceTemplate = resourceTemplate.trim();

            if (!resourceTemplate.isEmpty() && !resourceTemplate.equals("/")) {

                if (resourceTemplate.endsWith("/")) {
                    resourceTemplate = resourceTemplate.substring(0, resourceTemplate.length() - 1);
                }
                if (!resourceTemplate.startsWith("/")) {
                    resourceTemplate = "/" + resourceTemplate;
                }
            }
        }
        return resourceTemplate;
    }



    public ResourceTemplate<Result> withCommandName(String commandName) {
        this.commandName = commandName;
        return this;
    }

    public ResourceTemplate<Result> withCacheProvider(CacheProvider<Result> cacheProvider) {
        this.cacheProviders.add(cacheProvider);
        return this;
    }


    public Class<Result> getResultClass() {
        return resultClass;
    }

    public HttpRequest.Verb getVerb() {
        return verb;
    }

    public String getTemplate() {
        return uriTemplate;
    }


    /**
     * Build the URI from a List.  The URI template has been previously parsed e.g.
     * /dms/v1/device/{esn}/customer/{custId} parses into:
     * (String)/dms/v1/device/
     * (ResourceTemplate.Var)esn
     * (String)/customer/
     * (ResourceTemplate.Var)custId
     * <p/>
     * Resource.Builder's withTarget(name, value) will put the value in a map for later retrieval.
     * .withTarget("esn", "test-esn")
     * .withTarget("custId", 22)
     * <p/>
     * results in the map:
     * esn -> test-esn
     * custId -> 22
     * <p/>
     * when a value in the list of type String is found it is appended to the URI, if a value of type ResourceTemplate.Var is found
     * it is looked up in the variables map and appended to the list if it exists. if not the original {...} text is preserved
     * and a resulting uri of /dms/v1/device/test-esn/customer/22
     * <p/>
     * Avg time per call: 0.00218028 ms
     *
     * @param variables variables to inject into the URI
     * @return URI
     */

    public String toURIFragment(Map<String, String> variables) throws URISyntaxException {
        int params = variables.size();
        String uri = TemplateParser.toData(variables, uriTemplate, parsedList);

        if (params != 0 && !httpService.isExtraTargetVariablesOK()) {
            //TODO  one could determine the extra template variables at this point to provide a better message.
            throw new URISyntaxException(uriTemplate, String.format("extra template variable(s) supplied, variables: %s",
                    Arrays.toString(variables.keySet().toArray())));
        }
        return uri;
    }

    public ResourceTemplate<Result> withFallbackHandler(FallbackHandler<Result> fallbackHandler) {
        this.fallbackHandler = fallbackHandler;
        return this;
    }

    public FallbackHandler<Result> getFallbackHandler() {
        return fallbackHandler;
    }



    public ResourceTemplate<Result> withSecondaryCache(CacheConfig cacheConfig) {
        secondaryCacheConfig = cacheConfig;
        return this;
    }



    public ResourceTemplate<Result> withPrimaryCache(CacheConfig cacheConfig) {
        primaryCacheConfig = cacheConfig;
        return this;
    }

    public ResourceTemplate<Result> withResponseClass(Class classClass) {
        //todo
        return this;
    }


    public String getCommandName() {
        return commandName;
    }


    public boolean isSuccessfulHttpStatus(int statusCode) {
        return httpService.getSuccessfulHttpCodes().contains(Integer.valueOf(statusCode));
    }

    public boolean isErrorHttpStatus(int statusCode) {
        return httpService.getErrorHttpCodes().contains(Integer.valueOf(statusCode));
    }

    public boolean isAuthoritativeHttpStatus(int statusCode) {
        return isSuccessfulHttpStatus(statusCode) || isErrorHttpStatus(statusCode);
    }

    public Resource<Result> resource() {
        return new Resource<Result>(this);
    }

    public ResourceTemplate<Result> withResponseDeserializer() {
        //todo
        return this;
    }






}

