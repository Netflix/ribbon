package com.netflix.ribbonclientextensions.template;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by mcohen on 5/1/14.
 */
public class TemplateParser {

    public static List<Object> parseTemplate(String template) {
        List<Object> templateParts = new ArrayList<Object>();
        if (template == null) {
            return templateParts;
        }
        StringBuilder val = new StringBuilder();
        String key;
        for (char c : template.toCharArray()) {
            switch (c) {
                case '{':
                    key = val.toString();
                    val = new StringBuilder();
                    templateParts.add(key);
                    break;

                case '}':
                    key = val.toString();
                    val = new StringBuilder();
                    if (key.charAt(0) == ';') {
                        templateParts.add(new MatrixVar(key.substring(1)));
                    } else {
                        templateParts.add(new PathVar(key));
                    }
                    break;
                default:
                    val.append(c);
            }
        }
        key = val.toString();
        if (!key.isEmpty()) {
            templateParts.add(key);
        }
        return templateParts;
    }

    public static String toData(Map<String, String> variables, String template, List parsedList) throws URISyntaxException {
        int params = variables.size();
        // skip expansion if there's no valid variables set. ex. {a} is the
        // first valid
        if (variables.isEmpty() && template.indexOf('{') == 0) {
            return template;
        }

        StringBuilder builder = new StringBuilder();
        for (Object part : parsedList) {
            if (part instanceof TemplateVar) {
                String var = variables.get(part.toString());
                if (part instanceof MatrixVar) {
                    if (var != null) {
                        builder.append(';').append(part.toString()).append('=').append(var);
                        params--;
                    }
                } else if (part instanceof PathVar) {
                    if (var == null) {
                        throw new URISyntaxException(template, String.format("template variable %s was not supplied", part.toString()));
                    } else {
                        builder.append(var);
                        params--;
                    }
                } else {
                    throw new URISyntaxException(template, String.format("template variable type %s is not supplied", part.getClass().getCanonicalName()));
                }
            } else {
                builder.append(part.toString());
            }
        }

        return builder.toString();
    }
}
