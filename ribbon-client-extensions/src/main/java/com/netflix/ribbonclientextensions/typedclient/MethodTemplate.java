package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.typedclient.annotation.Content;
import com.netflix.ribbonclientextensions.typedclient.annotation.GET;
import com.netflix.ribbonclientextensions.typedclient.annotation.POST;
import com.netflix.ribbonclientextensions.typedclient.annotation.Path;
import com.netflix.ribbonclientextensions.typedclient.annotation.TemplateName;
import com.netflix.ribbonclientextensions.typedclient.annotation.Var;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.*;

/**
 * Extracts information from Ribbon annotated method, to automatically populate the Ribbon request template.
 * A few validations are performed as well:
 * - a return type must be {@link com.netflix.ribbonclientextensions.RibbonRequest}
 * - HTTP method must be always specified explicitly (there are no defaults)
 * - only parameter with {@link com.netflix.ribbonclientextensions.typedclient.annotation.Content} annotation is allowed
 *
 * @author Tomasz Bak
 */
public class MethodTemplate {
    public static enum HttpMethod {
        GET,
        POST
    }

    private static final MethodTemplate[] EMPTY_ARRAY = new MethodTemplate[]{};

    private final String templateName;
    private final HttpMethod httpMethod;
    private final Method method;
    private final String path;
    private final String[] paramNames;
    private final int[] valueIdxs;
    private final int contentArgPosition;

    public MethodTemplate(Method method) {
        this.method = method;
        this.httpMethod = extractHttpMethod();
        this.templateName = extractTemplateName();
        this.path = extractPathInfo();
        Object[] nameIdxPair = extractParamNamesWithIndexes();
        this.paramNames = (String[]) nameIdxPair[0];
        this.valueIdxs = (int[]) nameIdxPair[1];
        this.contentArgPosition = extractContentArgPosition();
        verifyResultType();
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public String getTemplateName() {
        return templateName;
    }

    public Method getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getParamNames(int idx) {
        return paramNames[idx];
    }

    public int getParamPosition(int idx) {
        return valueIdxs[idx];
    }

    public int getParamSize() {
        return paramNames.length;
    }

    public int getContentArgPosition() {
        return contentArgPosition;
    }

    public static <T> MethodTemplate[] from(Class<T> clientInterface) {
        List<MethodTemplate> list = new ArrayList<MethodTemplate>(clientInterface.getMethods().length);
        for (Method m : clientInterface.getMethods()) {
            list.add(new MethodTemplate(m));
        }
        return list.toArray(EMPTY_ARRAY);
    }

    private HttpMethod extractHttpMethod() {
        Annotation annotation = method.getAnnotation(GET.class);
        if (null != annotation) {
            return HttpMethod.GET;
        }
        annotation = method.getAnnotation(POST.class);
        if (null != annotation) {
            return HttpMethod.POST;
        }
        throw new IllegalArgumentException(format(
                "Method %s.%s does not specify HTTP method with @GET or @POST annotation",
                method.getDeclaringClass().getSimpleName(), method.getName()));
    }

    private String extractTemplateName() {
        TemplateName annotation = method.getAnnotation(TemplateName.class);
        return (annotation != null) ? annotation.value() : null;
    }

    private String extractPathInfo() {
        Path annotation = method.getAnnotation(Path.class);
        return (annotation != null) ? annotation.value() : null;
    }

    private Object[] extractParamNamesWithIndexes() {
        List<String> nameList = new ArrayList<String>();
        List<Integer> idxList = new ArrayList<Integer>();
        Annotation[][] params = method.getParameterAnnotations();
        for (int i = 0; i < params.length; i++) {
            for (Annotation a : params[i]) {
                if (a.annotationType().equals(Var.class)) {
                    String name = ((Var) a).value();
                    nameList.add(name);
                    idxList.add(i);
                }
            }
        }
        int size = nameList.size();
        String[] names = new String[size];
        int[] idxs = new int[size];
        for (int i = 0; i < size; i++) {
            names[i] = nameList.get(i);
            idxs[i] = idxList.get(i);
        }
        return new Object[]{names, idxs};
    }

    private int extractContentArgPosition() {
        Annotation[][] params = method.getParameterAnnotations();
        int pos = -1;
        int count = 0;
        for (int i = 0; i < params.length; i++) {
            for (Annotation a : params[i]) {
                if (a.annotationType().equals(Content.class)) {
                    pos = i;
                    count++;
                }
            }
        }
        if (count > 1) {
            throw new IllegalArgumentException(format(
                    "Method %s.%s annotates multiple parameters as @Content - at most one is allowed ",
                    method.getDeclaringClass().getSimpleName(), method.getName()));
        }
        return pos;
    }

    private void verifyResultType() {
        Class resultType = method.getReturnType();
        if (resultType.isAssignableFrom(RibbonRequest.class)) {
            return;
        }
        throw new IllegalArgumentException(format(
                "Method %s.%s must return Void or RibbonRequest<T> type not %s",
                method.getDeclaringClass().getSimpleName(), method.getName(), resultType.getSimpleName()));

    }
}
