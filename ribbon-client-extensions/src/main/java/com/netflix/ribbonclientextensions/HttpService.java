package com.netflix.ribbonclientextensions;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.HashSet;
import java.util.Set;
import org.apache.http.entity.ContentType;


@SuppressWarnings("UnusedDeclaration")
public class HttpService {
    public static final String X_NETFLIX_CLIENT_IMPLEMENTATION_VERSION = "X-Netflix.client.Implementation-Version";

    /*
        Either defaults == null and the rest are != null or defaults != null and the rest start out not null;
     */
    protected Set<Integer> successfulHttpCodes = null;
    protected Set<Integer> errorHttpCodes = null;
    protected Multimap<String, String> headers = null;
    protected Class errorClass = null;
    protected String restClientName = null;
    protected Boolean extraTargetVariablesOK = null;
    protected final HttpService defaults;

    public HttpService() {
        successfulHttpCodes = new HashSet<Integer>();
        errorHttpCodes = new HashSet<Integer>();
        headers = ArrayListMultimap.create();
        errorClass = null;
        restClientName = null;
        extraTargetVariablesOK = Boolean.FALSE;
        defaults = null;
    }


    public HttpService(HttpService defaults) {
        this.defaults = defaults;
    }

    /**
     * Adds a header to the list of headers to send.
     *
     * Headers are additive and will be added to any default headers.
     *
     * @param headerName Name of header
     * @param headerValue value for header.
     * @return this
     */
    public HttpService withHeader(String headerName, String headerValue) {
        ensureLocalHeaders();
        headers.put(headerName, headerValue);
        return this;
    }

    /**
     * Adds a header to the list of headers to send, removing any existing header with the same name first.
     * <p/>
     * Headers are additive and will be added to any default headers.
     *
     * @param headerName  Name of header
     * @param headerValue value for header.
     * @return this
     */
    public HttpService withUniqueHeader(String headerName, String headerValue) {
        ensureLocalHeaders();
        headers.removeAll(headerName);
        headers.put(headerName, headerValue);
        return this;
    }

    private void ensureLocalHeaders() {
        if (defaults != null && headers == null) {
            headers = ArrayListMultimap.create();
            headers.putAll(defaults.getHeaders());
        }
    }

    public Multimap<String, String> getHeaders() {
        return (defaults != null && headers == null) ? defaults.getHeaders() : headers;
    }

    public HttpService withErrorClass(Class errorClass) {
        this.errorClass = errorClass;
        return this;
    }

    public Class getErrorClass() {
        return (defaults != null && errorClass == null) ? defaults.getErrorClass() : errorClass;
    }


    /**
     * @param httpResults results that indicate a successful and expected completion of the request.
     * @return ResourceTemplate
     */
    public HttpService withSuccessHttpStatus(int... httpResults) {
        if (defaults != null && successfulHttpCodes == null) {
            successfulHttpCodes = new HashSet<Integer>();
            successfulHttpCodes.addAll(defaults.getSuccessfulHttpCodes());
        }
        for (int i : httpResults) {
            successfulHttpCodes.add(Integer.valueOf(i));
        }
        return this;
    }

    public Set<Integer> getSuccessfulHttpCodes() {
        return (defaults != null && successfulHttpCodes == null) ? defaults.getSuccessfulHttpCodes() : successfulHttpCodes;
    }

    /**
     * @param httpResults results tht are not success but the server may return under normal operations.  A client
     *                    should NEVER fall back on these results.
     * @return ResourceTemplate
     */
    public HttpService withErrorHttpCodes(int... httpResults) {
        if (defaults != null && errorHttpCodes == null) {
            errorHttpCodes = new HashSet<Integer>();
            errorHttpCodes.addAll(defaults.getErrorHttpCodes());
        }
        for (int i : httpResults) {
            errorHttpCodes.add(Integer.valueOf(i));
        }
        return this;
    }

    public Set<Integer> getErrorHttpCodes() {
        return (defaults != null && errorHttpCodes == null) ? defaults.getErrorHttpCodes() : errorHttpCodes;
    }


    /**
     * override the default contentType
     *
     * @param contentType content type
     * @return ResourceTemplate
     */
    public HttpService withContentType(ContentType contentType) {
        withUniqueHeader("Content-Type", contentType.getMimeType());
        return this;
    }

    /**
     * override the default accepted contentTypes
     *
     * @param contentTypes  content typ
     * @return ResourceTemplate
     */
    public HttpService withAcceptContentTypes(ContentType... contentTypes) {
        StringBuilder value = new StringBuilder();
        for (ContentType contentType : contentTypes) {
            if (value.length() != 0) {
                value.append(", ");
            }
            value.append(contentType);
        }
        withHeader("Accept", value.toString());
        return this;
    }

    public HttpService withClientVersion(Class clazz) {
        return withClientVersion(clazz.getPackage().getImplementationVersion());
    }

    public HttpService withClientVersion(String version) {
        withUniqueHeader(X_NETFLIX_CLIENT_IMPLEMENTATION_VERSION, version);
        return this;
    }

    public HttpService withRESTClientName(String restClientName) {
        this.restClientName = restClientName;
        return this;
    }

    public String getRESTClientName() {
        return (defaults != null && restClientName == null) ? defaults.getRESTClientName() : restClientName;
    }

    public HttpService allowExtraTargetVariables() {
        this.extraTargetVariablesOK = Boolean.TRUE;
        return this;
    }

    public HttpService noExtraTargetVariables() {
        this.extraTargetVariablesOK = Boolean.FALSE;
        return this;
    }

    public boolean isExtraTargetVariablesOK() {
        return (defaults != null && extraTargetVariablesOK == null) ? defaults.isExtraTargetVariablesOK() : extraTargetVariablesOK.booleanValue();
    }

}
