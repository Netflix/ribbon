package com.netflix.client.http;

import static com.netflix.utils.MultiMapUtil.getStringCollectionMap;

import com.sun.jersey.core.util.StringKeyStringValueIgnoreCaseMultivaluedMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class CaseInsensitiveMultiMap implements HttpHeaders {
    private final StringKeyStringValueIgnoreCaseMultivaluedMap map =
        new StringKeyStringValueIgnoreCaseMultivaluedMap();

    @Override
    public String getFirstValue(String headerName) {
        final List<String> strings = map.get(headerName);
        if (strings == null || strings.isEmpty()) {
            return null;
        } 
        return strings.get(0);
    }

    @Override
    public List<String> getAllValues(String headerName) {
        return map.get(headerName);
    }

    @Override
    public List<Entry<String, String>> getAllHeaders() {
        final Set<Entry<String, List<String>>> entries = map.entrySet();
        List<Entry<String, String>> list = new ArrayList<>();
        for (Entry<String, List<String>> entry : entries) {
            final List<String> values = entry.getValue();
            for (String value : values) {
                list.add(new SimpleEntry<>(entry.getKey(), value));
            }
        }
        return list;
    }

    @Override
    public boolean containsHeader(String name) {
        return map.containsKey(name.toLowerCase());
    }
    
    public void addHeader(String name, String value) {
        map.add(name, value);
    }
    
    Map<String, Collection<String>> asMap() {
        return getStringCollectionMap(map);
    }
}
